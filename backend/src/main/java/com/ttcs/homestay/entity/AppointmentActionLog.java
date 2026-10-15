package com.ttcs.homestay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointment_action_logs")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "receptionist_id")
    private Long receptionistId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public AppointmentActionLog(Long appointmentId, Long receptionistId, String actionType) {
        this.appointmentId = appointmentId;
        this.receptionistId = receptionistId;
        this.actionType = actionType;
        this.createdAt = OffsetDateTime.now();
    }
}
