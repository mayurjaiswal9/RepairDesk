package com.acetelecom.repairdesk.feature.notification.service;

import java.util.UUID;

/** Who a repair event is about, and the words that fill the message template. */
public final class NotificationContext {

    private UUID customerId;
    private UUID technicianId;
    private String reference;
    private String relatedType;
    private UUID relatedId;
    private String date;
    private String time;
    private String reason;
    private String detail;
    private String when;

    public static NotificationContext create() {
        return new NotificationContext();
    }

    public NotificationContext customer(UUID id) {
        this.customerId = id;
        return this;
    }

    public NotificationContext technician(UUID id) {
        this.technicianId = id;
        return this;
    }

    public NotificationContext reference(String reference) {
        this.reference = reference;
        return this;
    }

    public NotificationContext request(UUID id) {
        this.relatedType = "REPAIR_REQUEST";
        this.relatedId = id;
        return this;
    }

    public NotificationContext job(UUID id) {
        this.relatedType = "JOB";
        this.relatedId = id;
        return this;
    }

    public NotificationContext date(String date) {
        this.date = date;
        return this;
    }

    public NotificationContext time(String time) {
        this.time = time;
        return this;
    }

    public NotificationContext reason(String reason) {
        this.reason = reason;
        return this;
    }

    public NotificationContext detail(String detail) {
        this.detail = detail;
        return this;
    }

    public NotificationContext when(String when) {
        this.when = when;
        return this;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID technicianId() {
        return technicianId;
    }

    public String reference() {
        return reference;
    }

    public String relatedType() {
        return relatedType;
    }

    public UUID relatedId() {
        return relatedId;
    }

    public String date() {
        return date;
    }

    public String time() {
        return time;
    }

    public String reason() {
        return reason;
    }

    public String detail() {
        return detail;
    }

    public String when() {
        return when;
    }
}
