package cl.duoc.pedidos360.analitica.dto;

public class KafkaConsumerGroupDTO {

    private String groupId;
    private String state;
    private int membersCount;
    private String coordinator;

    public KafkaConsumerGroupDTO() {}

    public KafkaConsumerGroupDTO(String groupId, String state, int membersCount, String coordinator) {
        this.groupId = groupId;
        this.state = state;
        this.membersCount = membersCount;
        this.coordinator = coordinator;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getMembersCount() {
        return membersCount;
    }

    public void setMembersCount(int membersCount) {
        this.membersCount = membersCount;
    }

    public String getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(String coordinator) {
        this.coordinator = coordinator;
    }
}

