import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Icon } from '../../shared/icon/icon';
import { AuthService } from '../../core/services/auth.service';
import { BackendUrlService } from '../../core/services/backend-url.service';

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
  private readonly urls = inject(BackendUrlService);
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);

  readonly activeTab = signal<'list' | 'add-staff'>('list');
  readonly selectedRoleFilter = signal<string>('TODOS');

  // Formulario de nuevo personal
  readonly newName = signal('');
  readonly newEmail = signal('');
  readonly newPhone = signal('');
  readonly newPassword = signal('');
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
    this.http.get<{ success: boolean; data: any[] }>(`${this.urls.base('usuario')}`).subscribe({
      next: (res) => {
        if (res && res.data) {
          const mapped: StaffMember[] = res.data.map((u) => {
            const role: StaffMember['role'] = (u.rol as StaffMember['role']) || 'VENDEDOR';
            const rawDate = u.createdAt ? new Date(u.createdAt).toLocaleDateString('es-CL') : 'Fecha n/d';
            return {
              id: `EMP-${u.id}`,
              name: u.nombre,
              email: u.email,
              phone: u.telefono || 'Sin teléfono',
              role: role,
              shift: u.turno || (role === 'ADMIN' ? 'FULL_TIME' : role === 'VENDEDOR' ? 'MAÑANA' : 'TARDE'),
              status: (u.estado as 'ACTIVO' | 'INACTIVO') || 'ACTIVO',
              joinedDate: rawDate,
              avatarInitial: (u.nombre || 'U').charAt(0).toUpperCase(),
            };
          });
          this.staffList.set(mapped);
        }
      },
      error: () => {
        this.showToast('No se pudo cargar la información del personal desde el servidor.');
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
    const rawId = id.replace('EMP-', '');
    const currentMember = this.staffList().find((s) => s.id === id);
    if (!currentMember) return;

    const nextStatus = currentMember.status === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';

    this.http
      .put<{ success: boolean; data: any }>(
        `${this.urls.base('usuario')}/${rawId}/status?status=${nextStatus}`,
        {}
      )
      .subscribe({
        next: () => {
          this.staffList.update((list) =>
            list.map((s) => (s.id === id ? { ...s, status: nextStatus } : s))
          );
          this.showToast(`Estado de ${currentMember.name} actualizado a ${nextStatus} en base de datos.`);
        },
        error: (err) => {
          alert('No se pudo actualizar el estado en el backend: ' + (err.error?.message || 'Error de conexión'));
        },
      });
  }

  addStaffMember(): void {
    if (!this.newName().trim() || !this.newEmail().trim()) {
      alert('Por favor ingresa el nombre y el correo electrónico del empleado');
      return;
    }

    const generatedPassword = this.generateSecurePassword();
    const effectivePassword = this.newPassword().trim() || generatedPassword;

    if (effectivePassword.length < 6) {
      alert('La contraseña debe tener al menos 6 caracteres');
      return;
    }

    const payload = {
      nombre: this.newName().trim(),
      email: this.newEmail().trim(),
      password: effectivePassword,
      rol: this.newRole(),
      telefono: this.newPhone().trim() || null,
      turno: this.newShift(),
      estado: 'ACTIVO',
    };

    this.http.post<{ success: boolean; data: any }>(`${this.urls.base('usuario')}`, payload).subscribe({
      next: (res) => {
        const created = res.data;
        const newEmp: StaffMember = {
          id: `EMP-${created?.id || 0}`,
          name: payload.nombre,
          email: payload.email,
          phone: payload.telefono || 'Sin teléfono',
          role: payload.rol,
          shift: payload.turno,
          status: 'ACTIVO',
          joinedDate: 'Hoy',
          avatarInitial: payload.nombre.charAt(0).toUpperCase(),
        };

        this.staffList.update((list) => [newEmp, ...list]);
        const passMsg = this.newPassword().trim()
          ? ''
          : ` Contraseña autogenerada: ${generatedPassword}`;
        this.showToast(`¡Personal "${newEmp.name}" (${newEmp.role}) guardado en PostgreSQL!${passMsg}`);
        this.resetForm();
        this.activeTab.set('list');
      },
      error: (err) => {
        alert('No se pudo registrar el personal en el backend. ' + (err.error?.message || 'Error de conexión'));
      },
    });
  }

  resetForm(): void {
    this.newName.set('');
    this.newEmail.set('');
    this.newPhone.set('');
    this.newPassword.set('');
    this.newRole.set('VENDEDOR');
    this.newShift.set('FULL_TIME');
  }

  private generateSecurePassword(): string {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*';
    let result = 'P360-';
    for (let i = 0; i < 10; i++) {
      result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
  }

  private showToast(msg: string): void {
    this.toastMessage.set(msg);
    setTimeout(() => this.toastMessage.set(null), 5000);
  }
}
