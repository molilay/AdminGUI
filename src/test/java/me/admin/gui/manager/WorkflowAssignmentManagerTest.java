package me.admin.gui.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowAssignmentManagerTest {
    @Test void detectsRaceAndRequiresExplicitTakeover() {
        assertEquals(WorkflowAssignmentManager.ClaimResult.CLAIMED,
                WorkflowAssignmentManager.decideClaim("", "Alice", "", false));
        assertEquals(WorkflowAssignmentManager.ClaimResult.CONFLICT,
                WorkflowAssignmentManager.decideClaim("Bob", "Alice", "", true));
        assertEquals(WorkflowAssignmentManager.ClaimResult.CONFLICT,
                WorkflowAssignmentManager.decideClaim("Bob", "Alice", "Bob", false));
        assertEquals(WorkflowAssignmentManager.ClaimResult.CLAIMED,
                WorkflowAssignmentManager.decideClaim("Bob", "Alice", "Bob", true));
        assertEquals(WorkflowAssignmentManager.ClaimResult.ALREADY_OWNED,
                WorkflowAssignmentManager.decideClaim("Alice", "Alice", "Alice", false));
    }
}
