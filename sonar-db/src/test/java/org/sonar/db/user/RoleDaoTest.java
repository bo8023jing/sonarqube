/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.user;

import java.util.List;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.security.DefaultGroups;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.permission.GroupPermissionDto;
import org.sonar.db.permission.UserPermissionDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.component.ComponentTesting.newProjectDto;

public class RoleDaoTest {

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);

  private GroupDbTester groupDb = new GroupDbTester(dbTester);
  private DbClient dbClient = dbTester.getDbClient();
  private DbSession dbSession = dbTester.getSession();

  private RoleDao underTest = dbTester.getDbClient().roleDao();

  @Test
  public void select_user_permissions_by_permission_and_user_id() {
    insertUserPermission(UserRole.ADMIN, 1L, 2L);
    insertUserPermission(UserRole.ADMIN, 1L, 3L);
    // global permission - not returned
    insertUserPermission(UserRole.ADMIN, 1L, null);
    // project permission on another user id - not returned
    insertUserPermission(UserRole.ADMIN, 42L, 2L);
    // project permission on another permission - not returned
    insertUserPermission(GlobalPermissions.SCAN_EXECUTION, 1L, 2L);
    dbTester.commit();

    List<Long> result = underTest.selectComponentIdsByPermissionAndUserId(dbSession, UserRole.ADMIN, 1L);

    assertThat(result).hasSize(2).containsExactly(2L, 3L);
  }

  @Test
  public void select_group_permissions_by_permission_and_user_id() {
    long userId = 11L;

    underTest.insertGroupRole(dbSession, new GroupPermissionDto().setRole(UserRole.ADMIN).setGroupId(1L).setResourceId(2L));
    groupDb.addUserToGroup(userId, 1L);
    underTest.insertGroupRole(dbSession, new GroupPermissionDto().setRole(UserRole.ADMIN).setGroupId(2L).setResourceId(3L));
    groupDb.addUserToGroup(userId, 2L);
    // global permission - not returned
    groupDb.addUserToGroup(userId, 3L);
    underTest.insertGroupRole(dbSession, new GroupPermissionDto().setRole(UserRole.ADMIN).setGroupId(3L).setResourceId(null));
    // project permission on another user id - not returned
    underTest.insertGroupRole(dbSession, new GroupPermissionDto().setRole(UserRole.ADMIN).setGroupId(4L).setResourceId(4L));
    groupDb.addUserToGroup(12L, 4L);
    // project permission on another permission - not returned
    underTest.insertGroupRole(dbSession, new GroupPermissionDto().setRole(UserRole.USER).setGroupId(5L).setResourceId(5L));
    groupDb.addUserToGroup(userId, 5L);
    // duplicates on resource id - should be returned once
    insertUserPermission(UserRole.ADMIN, userId, 2L);
    underTest.insertGroupRole(dbSession, new GroupPermissionDto().setRole(UserRole.ADMIN).setGroupId(3L).setResourceId(3L));
    dbTester.commit();

    List<Long> result = underTest.selectComponentIdsByPermissionAndUserId(dbSession, UserRole.ADMIN, userId);

    assertThat(result).hasSize(2).containsExactly(2L, 3L);
  }

  @Test
  public void retrieve_global_group_permissions() {
    ComponentDto project = insertComponent(newProjectDto());
    GroupDto groupAdmin = insertGroup("sonar-administrators");
    GroupDto groupUser = insertGroup("sonar-users");
    insertGroupRole(null, groupAdmin, "admin");
    insertGroupRole(null, groupAdmin, "profileadmin");
    insertGroupRole(null, groupAdmin, "gateadmin");
    insertGroupRole(null, groupUser, "gateadmin");
    // Group 'anyone' has a NULL group_id
    insertGroupRole(null, null, "scan");
    insertGroupRole(null, null, "provisioning");
    // Component permission, it should not be returned with global permissions
    insertGroupRole(project, groupAdmin, "provisioning");

    assertThat(underTest.selectGroupPermissions(dbSession, groupAdmin.getName(), null)).containsOnly(GlobalPermissions.SYSTEM_ADMIN,
      GlobalPermissions.QUALITY_PROFILE_ADMIN,
      GlobalPermissions.QUALITY_GATE_ADMIN);
    assertThat(underTest.selectGroupPermissions(dbSession, groupUser.getName(), null)).containsOnly(GlobalPermissions.QUALITY_GATE_ADMIN);
    assertThat(underTest.selectGroupPermissions(dbSession, DefaultGroups.ANYONE, null)).containsOnly(GlobalPermissions.PROVISIONING,
      GlobalPermissions.SCAN_EXECUTION);
    assertThat(underTest.selectGroupPermissions(dbSession, "anyone", null)).containsOnly(GlobalPermissions.PROVISIONING, GlobalPermissions.SCAN_EXECUTION);
    assertThat(underTest.selectGroupPermissions(dbSession, "AnYoNe", null)).containsOnly(GlobalPermissions.PROVISIONING, GlobalPermissions.SCAN_EXECUTION);
  }

  @Test
  public void retrieve_resource_group_permissions() {
    ComponentDto project1 = insertComponent(newProjectDto());
    GroupDto groupAdmin = insertGroup("sonar-administrators");
    GroupDto groupUser = insertGroup("sonar-users");
    insertGroupRole(project1, groupAdmin, "admin");
    insertGroupRole(project1, groupAdmin, "codeviewer");
    insertGroupRole(project1, groupUser, "codeviewer");
    // Group 'anyone' has a NULL group_id
    insertGroupRole(project1, null, "user");
    // Global permission, it should not be returned with component permissions
    insertGroupRole(null, groupAdmin, "admin");

    assertThat(underTest.selectGroupPermissions(dbSession, groupAdmin.getName(), project1.getId())).containsOnly(UserRole.ADMIN, UserRole.CODEVIEWER);
    assertThat(underTest.selectGroupPermissions(dbSession, groupUser.getName(), project1.getId())).containsOnly(UserRole.CODEVIEWER);
  }

  @Test
  public void delete_global_group_permission() {
    ComponentDto project = insertComponent(newProjectDto());
    GroupDto groupAdmin = insertGroup("sonar-administrators");
    GroupDto groupUser = insertGroup("sonar-users");
    insertGroupRole(null, groupAdmin, "admin");
    insertGroupRole(null, groupAdmin, "profileadmin");
    insertGroupRole(null, groupAdmin, "gateadmin");
    insertGroupRole(null, groupUser, "gateadmin");
    // Group 'anyone' has a NULL group_id
    insertGroupRole(null, null, "scan");
    insertGroupRole(null, null, "provisioning");
    // Component permission, it should not be returned with global permissions
    insertGroupRole(project, groupAdmin, "provisioning");

    GroupPermissionDto groupRoleToDelete = new GroupPermissionDto().setGroupId(groupAdmin.getId()).setRole(GlobalPermissions.QUALITY_PROFILE_ADMIN);

    underTest.deleteGroupRole(groupRoleToDelete, dbSession);
    dbSession.commit();

    assertThat(dbClient.roleDao().selectGroupPermissions(dbSession, groupAdmin.getName(), null)).containsOnly("admin", "gateadmin");
  }

  @Test
  public void delete_resource_group_permission() {
    ComponentDto project1 = insertComponent(newProjectDto());
    GroupDto groupAdmin = insertGroup("sonar-administrators");
    GroupDto groupUser = insertGroup("sonar-users");
    insertGroupRole(project1, groupAdmin, "admin");
    insertGroupRole(project1, groupAdmin, "codeviewer");
    insertGroupRole(project1, groupUser, "codeviewer");
    // Group 'anyone' has a NULL group_id
    insertGroupRole(project1, null, "user");
    // Global permission, it should not be returned with component permissions
    insertGroupRole(null, groupAdmin, "admin");

    GroupPermissionDto groupRoleToDelete = new GroupPermissionDto().setGroupId(groupAdmin.getId()).setRole(UserRole.CODEVIEWER).setResourceId(project1.getId());

    underTest.deleteGroupRole(groupRoleToDelete, dbSession);
    dbSession.commit();

    assertThat(dbClient.roleDao().selectGroupPermissions(dbSession, groupAdmin.getName(), project1.getId())).containsOnly("admin");
  }

  @Test
  public void delete_all_group_permissions_by_group_id() {
    ComponentDto project1 = insertComponent(newProjectDto());
    GroupDto groupAdmin = insertGroup("sonar-administrators");
    GroupDto groupUser = insertGroup("sonar-users");
    GroupDto group3 = insertGroup("group3");
    insertGroupRole(null, groupAdmin, "admin");
    insertGroupRole(project1, groupAdmin, "profileadmin");
    insertGroupRole(null, groupAdmin, "gateadmin");
    insertGroupRole(null, groupUser, "gateadmin");
    insertGroupRole(null, null, "scan");
    insertGroupRole(null, null, "provisioning");
    insertGroupRole(project1, group3, "admin");

    underTest.deleteGroupRolesByGroupId(dbSession, groupAdmin.getId());
    dbSession.commit();

    assertThat(dbClient.roleDao().selectGroupPermissions(dbSession, groupAdmin.getName(), null)).isEmpty();
    assertThat(dbClient.roleDao().selectGroupPermissions(dbSession, groupAdmin.getName(), project1.getId())).isEmpty();
  }

  @Test
  public void should_count_component_permissions() {
    ComponentDto project = insertComponent(newProjectDto());
    UserDto user = insertUser("dave.loper");
    GroupDto group = insertGroup("devs");
    insertGroupRole(project, group, "codeviewer");
    insertUserRole(project, user, "user");

    assertThat(underTest.countComponentPermissions(dbSession, project.getId())).isEqualTo(2);
  }

  @Test
  public void should_remove_group_permissions_on_project() {
    ComponentDto project = insertComponent(newProjectDto());
    UserDto user = insertUser("dave.loper");
    GroupDto group = insertGroup("devs");
    insertUserRole(project, user, "user");
    insertGroupRole(project, group, "codeviewer");

    assertThat(underTest.selectGroupPermissions(dbSession, "devs", project.getId())).hasSize(1);
    assertThat(underTest.selectGroupPermissions(dbSession, "other", project.getId())).isEmpty();

    underTest.deleteGroupRolesByResourceId(dbSession, project.getId());
    dbSession.commit();

    assertThat(dbTester.countRowsOfTable("group_roles")).isEqualTo(0);
    assertThat(underTest.selectGroupPermissions(dbSession, "devs", project.getId())).isEmpty();
  }

  @Test
  public void count_users_with_one_specific_permission() {
    UserDto user = insertUser("bar");
    insertUserPermission(GlobalPermissions.SYSTEM_ADMIN, user.getId(), 123L);
    insertUserPermission(GlobalPermissions.SYSTEM_ADMIN, user.getId(), null);
    insertUserPermission(GlobalPermissions.SCAN_EXECUTION, user.getId(), null);

    int result = underTest.countUserPermissions(dbSession, GlobalPermissions.SYSTEM_ADMIN, null);

    assertThat(result).isEqualTo(1);
  }

  @Test
  public void count_users_with_one_permission_when_the_last_one_is_in_a_group() {
    UserDto user = insertUser("foo");
    GroupDto group = insertGroup("bar");
    dbClient.userGroupDao().insert(dbSession, new UserGroupDto()
      .setGroupId(group.getId())
      .setUserId(user.getId()));
    dbClient.roleDao().insertGroupRole(dbSession, new GroupPermissionDto()
      .setGroupId(group.getId())
      .setRole(GlobalPermissions.SYSTEM_ADMIN));

    int resultWithoutExcludingGroup = underTest.countUserPermissions(dbSession, GlobalPermissions.SYSTEM_ADMIN, null);
    int resultWithGroupExclusion = underTest.countUserPermissions(dbSession, GlobalPermissions.SYSTEM_ADMIN, group.getId());

    assertThat(resultWithoutExcludingGroup).isEqualTo(1);
    assertThat(resultWithGroupExclusion).isEqualTo(0);
  }

  @Test
  public void count_user_twice_when_user_and_group_permission() {
    UserDto user = insertUser("do");
    GroupDto group = insertGroup("bar");
    dbClient.userGroupDao().insert(dbSession, new UserGroupDto()
      .setGroupId(group.getId())
      .setUserId(user.getId()));
    dbClient.roleDao().insertGroupRole(dbSession, new GroupPermissionDto()
      .setGroupId(group.getId())
      .setRole(GlobalPermissions.SYSTEM_ADMIN));
    insertUserPermission(GlobalPermissions.SYSTEM_ADMIN, user.getId(), null);

    int result = underTest.countUserPermissions(dbSession, GlobalPermissions.SYSTEM_ADMIN, null);

    assertThat(result).isEqualTo(2);
  }

  private void insertUserPermission(String permission, long userId, @Nullable Long projectId) {
    dbTester.getDbClient().userPermissionDao().insert(dbSession, new org.sonar.db.permission.UserPermissionDto(permission, userId, projectId));
    dbSession.commit();
  }

  private GroupDto insertGroup(String groupName) {
    GroupDto dto = new GroupDto().setName(groupName).setOrganizationUuid(dbTester.getDefaultOrganizationUuid());
    dbClient.groupDao().insert(dbSession, dto);
    return dto;
  }

  private void insertGroupRole(@Nullable ComponentDto project, @Nullable GroupDto adminGroup, String role) {
    dbClient.roleDao().insertGroupRole(dbSession, new GroupPermissionDto()
      .setRole(role)
      .setGroupId(adminGroup == null ? null : adminGroup.getId())
      .setResourceId(project == null ? null : project.getId()));
    dbSession.commit();
  }

  private ComponentDto insertComponent(ComponentDto project) {
    dbClient.componentDao().insert(dbSession, project);
    dbSession.commit();
    return project;
  }

  private UserDto insertUser(String login) {
    UserDto dto = UserTesting.newUserDto(login, login, null);
    dbClient.userDao().insert(dbSession, dto);
    dbSession.commit();
    return dto;
  }

  private void insertUserRole(ComponentDto project, UserDto user, String role) {
    dbClient.userPermissionDao().insert(dbSession, new UserPermissionDto(role, user.getId(), project.getId()));
    dbSession.commit();
  }
}
