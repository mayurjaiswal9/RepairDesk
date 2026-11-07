package com.acetelecom.repairdesk.feature.notification.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Repair events an administrator can turn on, and the defaults for a new company.
 * WhatsApp and SMS are never on.
 */
public enum NotificationEvent {

    REQUEST_SUBMITTED("New repair request",
            "The office is told when a request is created.",
            "New repair request {reference} was submitted.",
            false, false, true, true, true, true, true),
    REQUEST_RECEIVED("Request received",
            "The customer is told their request was received.",
            "We received your repair request {reference}.",
            true, false, false, false, true, true, false),
    JOB_APPROVED("Request accepted",
            "The customer is told the request was accepted.",
            "Your repair request {reference} has been accepted and is being prepared.",
            true, false, false, false, true, true, false),
    REQUEST_REJECTED("Request declined",
            "The customer is told the request was declined.",
            "{message}",
            true, false, false, false, true, true, false),
    INFO_REQUIRED("More information needed",
            "The customer is asked for more information.",
            "More information is needed for your request {reference}: {message}",
            true, false, false, false, true, true, false),
    JOB_ASSIGNED("Job assigned",
            "The technician is told a job was assigned to them.",
            "You have been assigned to job {reference}.",
            false, true, false, false, true, true, true),
    JOB_UNASSIGNED("Job unassigned",
            "The technician is told a job was taken off them.",
            "You are no longer assigned to job {reference}.",
            false, true, false, false, true, true, true),
    JOB_IN_PROGRESS("Repair started",
            "The customer is told work has started.",
            "Work has started on your repair {reference}.",
            true, false, false, false, true, true, false),
    JOB_AWAITING_PARTS("Waiting for parts",
            "The customer is told the repair is waiting for parts.",
            "Your repair {reference} is on hold while we source parts.",
            true, false, false, false, true, true, false),
    JOB_RESUMED("Repair resumed",
            "The customer is told work has resumed.",
            "Work has resumed on your repair {reference}.",
            true, false, false, false, true, true, false),
    JOB_COMPLETED("Repair finished",
            "The customer is told the repair is ready.",
            "Your repair {reference} is complete and ready for collection.",
            true, false, false, false, true, true, false),
    JOB_CANCELLED("Repair cancelled",
            "The customer is told the repair was cancelled.",
            "Your repair {reference} has been cancelled. Reason: {reason}",
            true, false, false, false, true, true, false),
    APPOINTMENT_SCHEDULED("Appointment booked",
            "The customer is told an appointment was booked.",
            "Your repair appointment is scheduled for {date} at {time}.",
            true, false, false, false, true, true, false),
    APPOINTMENT_RESCHEDULED("Appointment moved",
            "The customer is told an appointment was moved.",
            "Your appointment has been rescheduled to {date} at {time}.",
            true, false, false, false, true, true, false),
    APPOINTMENT_CANCELLED("Appointment cancelled",
            "The customer is told an appointment was cancelled.",
            "Your appointment on {date} has been cancelled. {reason}",
            true, false, false, false, true, true, false),
    APPOINTMENT_SCHEDULED_TECHNICIAN("Appointment booked for technician",
            "The assigned technician is told about a new appointment.",
            "You have an appointment for {reference} on {date} at {time}.",
            false, true, false, false, true, true, true),
    APPOINTMENT_RESCHEDULED_TECHNICIAN("Appointment moved for technician",
            "The assigned technician is told an appointment moved.",
            "Your appointment for {reference} moved to {date} at {time}.",
            false, true, false, false, true, true, true),
    APPOINTMENT_CANCELLED_TECHNICIAN("Appointment cancelled for technician",
            "The assigned technician is told an appointment was cancelled.",
            "The appointment for {reference} on {date} was cancelled.",
            false, true, false, false, true, true, true),
    DAY_ENDED("Work day ended",
            "The technician is told the day was closed automatically.",
            "Your work day ended automatically at {when}.",
            false, true, false, false, true, true, true);

    private final String label;
    private final String description;
    private final String template;
    private final boolean customer;
    private final boolean technician;
    private final boolean staff;
    private final boolean admin;
    private final boolean inApp;
    private final boolean phone;
    private final boolean desktop;

    NotificationEvent(String label, String description, String template,
            boolean customer, boolean technician, boolean staff, boolean admin,
            boolean inApp, boolean phone, boolean desktop) {
        this.label = label;
        this.description = description;
        this.template = template;
        this.customer = customer;
        this.technician = technician;
        this.staff = staff;
        this.admin = admin;
        this.inApp = inApp;
        this.phone = phone;
        this.desktop = desktop;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String template() {
        return template;
    }

    public NotificationRule newRule(UUID companyId) {
        NotificationRule rule = new NotificationRule();
        rule.setCompanyId(companyId);
        rule.setEventKey(name());
        rule.setEnabled(this != JOB_UNASSIGNED && this != JOB_RESUMED
                && this != APPOINTMENT_SCHEDULED_TECHNICIAN
                && this != APPOINTMENT_RESCHEDULED_TECHNICIAN
                && this != APPOINTMENT_CANCELLED_TECHNICIAN);
        rule.setMessageTemplate(template);
        rule.setNotifyCustomer(customer);
        rule.setNotifyTechnician(technician);
        rule.setNotifyStaff(staff);
        rule.setNotifyAdmin(admin);
        rule.setChannelInApp(inApp);
        rule.setChannelPhone(phone);
        rule.setChannelDesktop(desktop);
        rule.setChannelWhatsapp(false);
        rule.setChannelSms(false);
        return rule;
    }

    public static Optional<NotificationEvent> find(String key) {
        return Arrays.stream(values()).filter(event -> event.name().equals(key)).findFirst();
    }
}
