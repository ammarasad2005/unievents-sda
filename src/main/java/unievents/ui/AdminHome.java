package unievents.ui;

import unievents.application.ClubController;
import unievents.application.ConflictController;
import unievents.domain.Admin;

/**
 * Entry point for the admin role: navigation for UC11 (Create Club) and
 * UC12 (Resolve Registration Conflict).
 */
public final class AdminHome {

    private AdminHome() {
    }

    public static javafx.scene.Node create(UiContext ctx, Admin admin) {
        ClubController clubController = new ClubController(admin, UniEventsApp.DATA);
        ConflictController conflictController = new ConflictController(admin, UniEventsApp.DATA);

        Shell shell = Shell.create(ctx, "UniEvents · Admin");
        shell.nav("Create Club (UC11)", () -> CreateClubScreen.create(clubController, ctx));
        shell.nav("Resolve Conflicts (UC12)", () -> ConflictScreen.create(conflictController, ctx));
        return shell.show(CreateClubScreen.create(clubController, ctx)).node();
    }
}
