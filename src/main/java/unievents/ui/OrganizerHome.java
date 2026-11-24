package unievents.ui;

import unievents.application.ManagementController;
import unievents.domain.ClubOrganizer;

/**
 * Entry point for the club organizer role: navigation for UC6–UC10.
 */
public final class OrganizerHome {

    private OrganizerHome() {
    }

    public static javafx.scene.Node create(UiContext ctx, ClubOrganizer organizer) {
        ManagementController controller = new ManagementController(organizer, UniEventsApp.DATA);

        Shell shell = Shell.create(ctx, "UniEvents · Organizer");
        shell.nav("My Events (UC6–UC10)", () -> MyEventsScreen.create(controller, ctx));
        shell.nav("Create Event (UC6)", () -> EventFormScreen.create(controller, ctx, null));
        shell.nav("Change Status (UC7)", () -> EventStatusScreen.create(controller, ctx));
        shell.nav("Send Announcement (UC8)", () -> AnnouncementScreen.create(controller, ctx));
        shell.nav("Attendance Sheet (UC9)", () -> AttendanceScreen.create(controller, ctx));
        shell.nav("Attendance Report (UC10)", () -> AttendanceReportScreen.create(controller, ctx));
        return shell.show(MyEventsScreen.create(controller, ctx)).node();
    }
}
