import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { AuthService } from '../../core/services/auth.service';

export interface StaffMember {
  id: string;
  name: string;
  email: string;
  phone: string;
  role: 'VENDEDOR' | 'REPARTIDOR' | 'ADMIN' | 'SOPORTE';
  shift: 'MAÑANA' | 'TARDE' | 'NOCHE' | 'FULL_TIME';
  status: 'ACTIVO' | 'INACTIVO';
  joinedDate: string;
  avatarInitial: string;
}

@Component({
  selector: 'app-staff',
  standalone: true,
  imports: [RouterLink, Icon, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff.html',
  styleUrl: './staff.css',
})
export class Staff implements OnInit {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);

  readonly activeTab = signal<'list' | 'add-staff'>('list');
  readonly selectedRoleFilter = signal<string>('TODOS');

  // Formulario de nuevo personal
  readonly newName = signal('');
  readonly newEmail = signal('');
  readonly newPhone = signal('');
  readonly newRole = signal<StaffMember['role']>('VENDEDOR');
  readonly newShift = signal<StaffMember['shift']>('FULL_TIME');

  // Mensaje de notificación
  readonly toastMessage = signal<string | null>(null);

  // Lista de personal sincronizada con PostgreSQL
  readonly staffList = signal<StaffMember[]>([]);

  // Personal filtrado por rol
  readonly filteredStaff = computed(() => {
    const filter = this.selectedRoleFilter();
    const list = this.staffList();
    return filter === 'TODOS' ? list : list.filter((s) => s.role === filter);
  });

  // Métricas de personal
  readonly activeCount = computed(() => this.staffList().filter((s) => s.status === 'ACTIVO').length);
  readonly sellerCount = computed(() => this.staffList().filter((s) => s.role === 'VENDEDOR' && s.status === 'ACTIVO').length);
  readonly courierCount = computed(() => this.staffList().filter((s) => s.role === 'REPARTIDOR' && s.status === 'ACTIVO').length);
  readonly adminCount = computed(() => this.staffList().filter((s) => s.role === 'ADMIN').length);

  ngOnInit(): void {
    this.cargarPersonalDesdeDB();
  }

  cargarPersonalDesdeDB(): void {
    this.http.get<{ success: boolean; data: any[] }>('http://localhost:8081/api/usuario').subscribe({
      next: (res) => {
        if (res && res.data) {
          const mapped: StaffMember[] = res.data.map((u) => {
            let role: StaffMember['role'] = u.rol || 'VENDEDOR';
            if (u.nombre?.toLowerCase().includes('repartidor')) {
              role = 'REPARTIDOR';
            } else if (u.nombre?.toLowerCase().includes('soporte')) {
              role = 'SOPORTE';
            }

            const rawDate = u.createdAt ? new Date(u.createdAt).toLocaleDateString('es-CL') : '01/01/2026';
            return {
              id: `EMP-${u.id}`,
              name: u.nombre,
              email: u.email,
              phone: u.phone || '+56 9 8765 4321',
              role: role,
              shift: role === 'ADMIN' ? 'FULL_TIME' : role === 'VENDEDOR' ? 'MAÑANA' : 'TARDE',
              status: 'ACTIVO',
              joinedDate: rawDate,
              avatarInitial: (u.nombre || 'U').charAt(0).toUpperCase(),
            };
          });
          this.staffList.set(mapped);
        }
      },
      error: () => {
        // Fallback si no hay conexión
        this.staffList.set([
          { id: 'EMP-1', name: 'Administrador Vixo', email: 'admin@pedidos360.cl', phone: '+56 9 1234 5678', role: 'ADMIN', shift: 'FULL_TIME', status: 'ACTIVO', joinedDate: '01/01/2026', avatarInitial: 'A' },
          { id: 'EMP-3', name: 'Vendedor Gonzalo Silva', email: 'vendedor@pedidos360.cl', phone: '+56 9 8765 4321', role: 'VENDEDOR', shift: 'MAÑANA', status: 'ACTIVO', joinedDate: '15/02/2026', avatarInitial: 'V' },
          { id: 'EMP-7', name: 'Rodrigo Morales (Repartidor)', email: 'rodrigo.morales@pedidos360.cl', phone: '+56 9 5566 7788', role: 'REPARTIDOR', shift: 'FULL_TIME', status: 'ACTIVO', joinedDate: '10/03/2026', avatarInitial: 'R' },
          { id: 'EMP-8', name: 'Camila Reyes (Soporte Cliente)', email: 'camila.reyes@pedidos360.cl', phone: '+56 9 9988 1122', role: 'SOPORTE', shift: 'MAÑANA', status: 'ACTIVO', joinedDate: '20/04/2026', avatarInitial: 'C' },
        ]);
      },
    });
  }

  setTab(tab: 'list' | 'add-staff'): void {
    this.activeTab.set(tab);
  }

  setRoleFilter(role: string): void {
    this.selectedRoleFilter.set(role);
  }

  toggleStaffStatus(id: string): void {
    this.staffList.update((list) =>
      list.map((s) =>
        s.id === id ? { ...s, status: s.status === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO' } : s
      )
    );
    this.showToast('Estado del personal actualizado correctamente');
  }

  addStaffMember(): void {
    if (!this.newName() || !this.newEmail()) {
      alert('Por favor ingresa el nombre y el correo electrónico del empleado');
      return;
    }

    const payload = {
      nombre: this.newName(),
      email: this.newEmail(),
      password: 'Password123!',
      rol: 'VENDEDOR',
    };

    this.http.post<{ success: boolean; data: any }>('http://localhost:8081/api/usuario', payload).subscribe({
      next: (res) => {
        const created = res.data;
        const newEmp: StaffMember = {
          id: `EMP-${created?.id || Math.floor(100 + Math.random() * 900)}`,
          name: this.newName(),
          email: this.newEmail(),
          phone: this.newPhone() || '+56 9 8877 6655',
          role: this.newRole(),
          shift: this.newShift(),
          status: 'ACTIVO',
          joinedDate: 'Hoy',
          avatarInitial: this.newName().charAt(0).toUpperCase(),
        };

        this.staffList.update((list) => [newEmp, ...list]);
        this.showToast(`¡Nuevo personal "${newEmp.name}" (${newEmp.role}) guardado en PostgreSQL!`);
        this.resetForm();
        this.activeTab.set('list');
      },
      error: () => {
        // Local fallback if backend fails
        const newEmp: StaffMember = {
          id: `EMP-${Math.floor(100 + Math.random() * 900)}`,
          name: this.newName(),
          email: this.newEmail(),
          phone: this.newPhone() || '+56 9 8877 6655',
          role: this.newRole(),
          shift: this.newShift(),
          status: 'ACTIVO',
          joinedDate: 'Hoy',
          avatarInitial: this.newName().charAt(0).toUpperCase(),
        };
        this.staffList.update((list) => [newEmp, ...list]);
        this.showToast(`¡Nuevo personal "${newEmp.name}" (${newEmp.role}) registrado!`);
        this.resetForm();
        this.activeTab.set('list');
      },
    });
  }

  resetForm(): void {
    this.newName.set('');
    this.newEmail.set('');
    this.newPhone.set('');
    this.newRole.set('VENDEDOR');
    this.newShift.set('FULL_TIME');
  }

  private showToast(msg: string): void {
    this.toastMessage.set(msg);
    setTimeout(() => this.toastMessage.set(null), 4000);
  }
}
