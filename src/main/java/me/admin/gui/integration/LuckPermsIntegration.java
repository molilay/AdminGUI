package me.admin.gui.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.ChatMetaNode;
import net.luckperms.api.node.types.DisplayNameNode;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.WeightNode;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class LuckPermsIntegration {

    private LuckPerms api;
    private static final QueryOptions DEFAULT_QUERY = QueryOptions.nonContextual();

    public boolean setup() {
        try {
            this.api = LuckPermsProvider.get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private LuckPerms getApi() {
        if (api == null && !setup()) throw new IllegalStateException("LuckPerms API is not available");
        return api;
    }

    public GroupManager getGroupManager() {
        return getApi().getGroupManager();
    }

    public UserManager getUserManager() {
        return getApi().getUserManager();
    }

    public Set<Group> getAllGroups() {
        return new HashSet<>(getGroupManager().getLoadedGroups());
    }

    public List<Group> getSortedGroups() {
        List<Group> groups = new ArrayList<>(getAllGroups());
        groups.sort((a, b) -> {
            int weightA = a.getWeight().isPresent() ? a.getWeight().getAsInt() : 0;
            int weightB = b.getWeight().isPresent() ? b.getWeight().getAsInt() : 0;
            return Integer.compare(weightB, weightA);
        });
        return groups;
    }

    public Group getGroup(String name) {
        return getGroupManager().getGroup(name);
    }

    public CompletableFuture<Group> createGroup(String name) {
        return getGroupManager().createAndLoadGroup(name);
    }

    public CompletableFuture<Void> deleteGroup(Group group) {
        return getGroupManager().deleteGroup(group);
    }

    public String getGroupPrefix(Group group) {
        return group.getCachedData().getMetaData(DEFAULT_QUERY).getPrefix();
    }

    public String getGroupSuffix(Group group) {
        return group.getCachedData().getMetaData(DEFAULT_QUERY).getSuffix();
    }

    public int getGroupWeight(Group group) {
        return group.getWeight().orElse(0);
    }

    public Set<Group> getParentGroups(Group group) {
        Set<Group> parents = new HashSet<>();
        for (Node node : group.getNodes()) {
            if (node instanceof InheritanceNode inheritanceNode) {
                Group parent = getGroup(inheritanceNode.getGroupName());
                if (parent != null) parents.add(parent);
            }
        }
        return parents;
    }

    public CompletableFuture<Void> addParent(Group group, Group parent) {
        group.data().add(InheritanceNode.builder(parent).build());
        return getGroupManager().saveGroup(group);
    }

    public CompletableFuture<Void> removeParent(Group group, Group parent) {
        group.data().remove(InheritanceNode.builder(parent).build());
        return getGroupManager().saveGroup(group);
    }

    public CompletableFuture<Void> addPermission(Group group, String permission, boolean value) {
        group.data().add(PermissionNode.builder(permission).value(value).build());
        return getGroupManager().saveGroup(group);
    }

    public CompletableFuture<Void> removePermission(Group group, String permission) {
        group.data().remove(PermissionNode.builder(permission).build());
        return getGroupManager().saveGroup(group);
    }

    public Set<PermissionNode> getPermissions(Group group) {
        return group.getNodes(NodeType.PERMISSION).stream().collect(Collectors.toSet());
    }

    public CompletableFuture<Void> setGroupWeight(Group group, int weight) {
        group.data().clear(n -> n.getType() == NodeType.WEIGHT);
        group.data().add(WeightNode.builder(weight).build());
        return getGroupManager().saveGroup(group);
    }

    public CompletableFuture<Void> setGroupPrefix(Group group, String prefix) {
        group.data().clear(node -> node instanceof ChatMetaNode<?, ?> chatMeta
                && chatMeta.getMetaType() == net.luckperms.api.node.ChatMetaType.PREFIX);
        group.data().add(net.luckperms.api.node.ChatMetaType.PREFIX.builder(prefix, 100).build());
        return getGroupManager().saveGroup(group);
    }

    public CompletableFuture<Void> setGroupSuffix(Group group, String suffix) {
        group.data().clear(node -> node instanceof ChatMetaNode<?, ?> chatMeta
                && chatMeta.getMetaType() == net.luckperms.api.node.ChatMetaType.SUFFIX);
        group.data().add(net.luckperms.api.node.ChatMetaType.SUFFIX.builder(suffix, 100).build());
        return getGroupManager().saveGroup(group);
    }

    public CompletableFuture<Void> renameGroup(Group group, String newName) {
        group.data().clear(n -> n.getType() == NodeType.DISPLAY_NAME);
        group.data().add(DisplayNameNode.builder(newName).build());
        return getGroupManager().saveGroup(group);
    }

    public CompletableFuture<User> getUser(UUID uuid) {
        return getUserManager().loadUser(uuid);
    }

    /** Loaded-only compatibility accessor. Never waits on the Bukkit main thread. */
    public User getUserSync(UUID uuid) {
        return getUserManager().getUser(uuid);
    }

    public java.util.OptionalInt findUserWeight(UUID uuid) {
        User user = getUserManager().getUser(uuid);
        if (user == null) return java.util.OptionalInt.empty();
        Group primary = getGroup(user.getPrimaryGroup());
        return java.util.OptionalInt.of(primary == null ? 0 : primary.getWeight().orElse(0));
    }

    public Optional<Boolean> findUserPermission(UUID uuid, String permission) {
        User user = getUserManager().getUser(uuid);
        if (user == null) return Optional.empty();
        return Optional.of(user.getCachedData().getPermissionData(DEFAULT_QUERY)
                .checkPermission(permission).asBoolean());
    }

    public List<Group> getPlayerGroups(UUID uuid) {
        User user = getUserManager().getUser(uuid);
        if (user == null) return new ArrayList<>();
        List<Group> groups = new ArrayList<>();
        for (Node node : user.getNodes()) {
            if (node instanceof InheritanceNode inheritanceNode) {
                Group group = getGroup(inheritanceNode.getGroupName());
                if (group != null) groups.add(group);
            }
        }
        return groups;
    }

    public String getPrimaryGroup(UUID uuid) {
        User user = getUserManager().getUser(uuid);
        if (user == null) return "default";
        return user.getPrimaryGroup();
    }

    public int getLoadedUserWeight(UUID uuid) {
        User user = getUserManager().getUser(uuid);
        if (user == null) return 0;
        Group primary = getGroup(user.getPrimaryGroup());
        return primary == null ? 0 : primary.getWeight().orElse(0);
    }

    public String getLoadedPrimaryGroup(UUID uuid) {
        User user = getUserManager().getUser(uuid);
        return user == null ? "default" : user.getPrimaryGroup();
    }

    public CompletableFuture<java.util.OptionalInt> findUserWeightAsync(UUID uuid) {
        return getUser(uuid).thenApply(user -> {
            Group primary = getGroup(user.getPrimaryGroup());
            return java.util.OptionalInt.of(primary == null ? 0 : primary.getWeight().orElse(0));
        });
    }

    public CompletableFuture<Optional<Boolean>> findUserPermissionAsync(UUID uuid, String permission) {
        return getUser(uuid).thenApply(user -> Optional.of(user.getCachedData().getPermissionData(DEFAULT_QUERY)
                .checkPermission(permission).asBoolean()));
    }

    /** Loads a detached immutable permission snapshot suitable for a GUI callback. */
    public CompletableFuture<List<PermissionNode>> getPermissionsAsync(UUID uuid) {
        return getUser(uuid).thenApply(user -> user.getNodes(NodeType.PERMISSION).stream()
                .sorted(Comparator.comparing(PermissionNode::getPermission))
                .toList());
    }

    /** Atomic LuckPerms mutation; persistence remains owned by LuckPerms' executor. */
    public CompletableFuture<Void> setUserPermissionAsync(UUID uuid, String permission, boolean value) {
        return getUserManager().modifyUser(uuid, user -> {
            user.data().clear(node -> node instanceof PermissionNode permissionNode
                    && permissionNode.getPermission().equalsIgnoreCase(permission));
            user.data().add(PermissionNode.builder(permission).value(value).build());
        });
    }

    public void setGroup(Player player, Group group) {
        setGroup(player.getUniqueId(), player.getName(), group, null);
    }

    public void setGroup(UUID uuid, String playerName, Group group, Long durationSeconds) {
        getUserManager().modifyUser(uuid, user -> {
            user.data().clear(n -> n instanceof InheritanceNode);
            InheritanceNode.Builder builder = InheritanceNode.builder(group);
            if (durationSeconds != null && durationSeconds > 0) {
                long expiry = System.currentTimeMillis() + (durationSeconds * 1000);
                builder.expiry(expiry);
            }
            user.data().add(builder.build());
            user.setPrimaryGroup(group.getName());
        });
    }

    public void addGroup(UUID uuid, Group group, Long durationSeconds) {
        getUserManager().modifyUser(uuid, user -> {
            InheritanceNode.Builder builder = InheritanceNode.builder(group);
            if (durationSeconds != null && durationSeconds > 0) {
                long expiry = System.currentTimeMillis() + (durationSeconds * 1000);
                builder.expiry(expiry);
            }
            user.data().add(builder.build());
        });
    }

    public void removeGroup(UUID uuid, Group group) {
        getUserManager().modifyUser(uuid, user -> {
            user.data().remove(InheritanceNode.builder(group).build());
        });
    }

    public long getPlayerCountInGroup(Group group) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> getPlayerGroups(p.getUniqueId()).contains(group))
                .count();
    }
}
