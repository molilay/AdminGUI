package me.admin.gui.database;

import java.time.LocalDateTime;

public class LogEntry {

    private final int id;
    private final String type;
    private final String moderator;
    private final String target;
    private final String reason;
    private final LocalDateTime date;
    private final long duration;

    public LogEntry(int id, String type, String moderator, String target, String reason, LocalDateTime date, long duration) {
        this.id = id;
        this.type = type;
        this.moderator = moderator;
        this.target = target;
        this.reason = reason;
        this.date = date;
        this.duration = duration;
    }

    public int getId() { return id; }
    public String getType() { return type; }
    public String getModerator() { return moderator; }
    public String getTarget() { return target; }
    public String getReason() { return reason; }
    public LocalDateTime getDate() { return date; }
    public long getDuration() { return duration; }
}
