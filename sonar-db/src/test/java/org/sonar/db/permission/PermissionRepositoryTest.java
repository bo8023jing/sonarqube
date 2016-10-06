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
package org.sonar.db.permission;

import java.util.Date;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.MapSettings;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.permission.template.PermissionTemplateCharacteristicDto;
import org.sonar.db.permission.template.PermissionTemplateDbTester;
import org.sonar.db.permission.template.PermissionTemplateDto;
import org.sonar.db.user.GroupDbTester;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.RoleDao;
import org.sonar.db.user.UserDbTester;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserTesting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.core.permission.GlobalPermissions.SCAN_EXECUTION;

public class PermissionRepositoryTest {

  private static final String DEFAULT_TEMPLATE = "default_20130101_010203";
  private static final ComponentDto PROJECT = new ComponentDto().setId(123L).setUuid("THE_PROJECT_UUID");
  private static final long NOW = 123456789L;

  private System2 system2 = mock(System2.class);

  @Rule
  public ExpectedException throwable = ExpectedException.none();
  @Rule
  public DbTester dbTester = DbTester.create(system2);

  private GroupDbTester groupDb = new GroupDbTester(dbTester);
  private UserDbTester userDb = new UserDbTester(dbTester);
  private PermissionTemplateDbTester templateDb = new PermissionTemplateDbTester(dbTester);
  private DbSession session = dbTester.getSession();
  private DbClient dbClient = dbTester.getDbClient();
  private Settings settings = new MapSettings();
  private PermissionRepository underTest = new PermissionRepository(dbClient, settings);

  @Before
  public void setUp() {
    when(system2.now()).thenReturn(NOW);
  }

  @Test
  public void apply_permission_template() {
    ComponentDto project1 = insertComponent(ComponentTesting.newProjectDto());
    ComponentDto project2 = insertComponent(ComponentTesting.newProjectDto());
    GroupDto adminGroup = insertGroup("sonar-administrators");
    GroupDto userGroup = insertGroup("sonar-users");
    UserDto user = insertUser("marius");
    insertGroupRole(project1, adminGroup, "admin");
    insertGroupRole(project1, userGroup, "user");
    insertUserRole(project1, user, "admin");
    PermissionTemplateDto permissionTemplate = insertPermissionTemplate(DEFAULT_TEMPLATE);
    insertPermTemplateGroup(permissionTemplate, adminGroup, "admin");
    insertPermTemplateGroup(permissionTemplate, adminGroup, "issueadmin");
    insertPermTemplateGroup(permissionTemplate, userGroup, "user");
    insertPermTemplateGroup(permissionTemplate, userGroup, "codeviewer");
    insertPermTemplateGroup(permissionTemplate, null, "user");
    insertPermTemplateGroup(permissionTemplate, null, "codeviewer");
    insertPermTemplateUser(permissionTemplate, user, "admin");

    RoleDao roleDao = dbTester.getDbClient().roleDao();
    assertThat(roleDao.selectGroupPermissions(session, "sonar-administrators", project2.getId())).isEmpty();
    assertThat(roleDao.selectGroupPermissions(session, "sonar-users", project2.getId())).isEmpty();
    assertThat(roleDao.selectGroupPermissions(session, "Anyone", project2.getId())).isEmpty();
    assertThat(dbTester.getDbClient().userPermissionDao().selectPermissionsByLogin(session, "marius", project2.uuid())).isEmpty();

    underTest.applyPermissionTemplate(session, permissionTemplate.getUuid(), project2);

    assertThat(roleDao.selectGroupPermissions(session, "sonar-administrators", project2.getId())).containsOnly("admin", "issueadmin");
    assertThat(roleDao.selectGroupPermissions(session, "sonar-users", project2.getId())).containsOnly("user", "codeviewer");
    assertThat(roleDao.selectGroupPermissions(session, "Anyone", project2.getId())).containsOnly("user", "codeviewer");
    assertThat(dbTester.getDbClient().userPermissionDao().selectPermissionsByLogin(session, "marius", project2.uuid())).containsOnly("admin");

    checkAuthorizationUpdatedAtIsUpdated(project2);
  }

  @Test
  public void apply_default_permission_template_from_component_id() {
    ComponentDto project1 = insertComponent(ComponentTesting.newProjectDto());
    ComponentDto project2 = insertComponent(ComponentTesting.newProjectDto());
    GroupDto groupAdmin = insertGroup("sonar-administrators");
    GroupDto groupUser = insertGroup("sonar-users");
    UserDto user = insertUser("marius");
    insertGroupRole(project1, groupAdmin, "admin");
    insertGroupRole(project1, groupUser, "user");
    insertUserRole(project1, user, "admin");
    PermissionTemplateDto permissionTemplate = insertPermissionTemplate(DEFAULT_TEMPLATE);
    insertPermTemplateGroup(permissionTemplate, groupAdmin, "admin");
    insertPermTemplateGroup(permissionTemplate, groupAdmin, "issueadmin");
    insertPermTemplateGroup(permissionTemplate, groupUser, "user");
    insertPermTemplateGroup(permissionTemplate, groupUser, "codeviewer");
    insertPermTemplateGroup(permissionTemplate, null, "user");
    insertPermTemplateGroup(permissionTemplate, null, "codeviewer");
    insertPermTemplateUser(permissionTemplate, user, "admin");
    insertPermTemplateCharacteristic(permissionTemplate, "user", true);
    PermissionTemplateDto permissionTemplate2 = insertPermissionTemplate("other");
    insertPermTemplateCharacteristic(permissionTemplate2, "user", false);
    settings.setProperty("sonar.permission.template.default", DEFAULT_TEMPLATE);

    underTest.applyDefaultPermissionTemplate(session, project2.getId());
    session.commit();

    assertThat(dbClient.userPermissionDao().selectPermissionsByLogin(session, user.getLogin(), project2.uuid())).containsOnly("admin");
  }

  @Test
  public void apply_default_permission_template_from_component() {
    ComponentDto project1 = insertComponent(ComponentTesting.newProjectDto());
    ComponentDto project2 = insertComponent(ComponentTesting.newProjectDto());
    GroupDto groupAdmin = insertGroup("sonar-administrators");
    GroupDto groupUser = insertGroup("sonar-users");
    UserDto userMarius = insertUser("marius");
    UserDto userJanette = insertUser("janette");
    insertGroupRole(project1, groupAdmin, "admin");
    insertGroupRole(project1, groupUser, "user");
    insertUserRole(project1, userMarius, "admin");
    PermissionTemplateDto permissionTemplate = insertPermissionTemplate(DEFAULT_TEMPLATE);
    insertPermTemplateGroup(permissionTemplate, groupAdmin, "admin");
    insertPermTemplateGroup(permissionTemplate, groupAdmin, "issueadmin");
    insertPermTemplateGroup(permissionTemplate, groupUser, "user");
    insertPermTemplateGroup(permissionTemplate, groupUser, "codeviewer");
    insertPermTemplateGroup(permissionTemplate, null, "user");
    insertPermTemplateGroup(permissionTemplate, null, "codeviewer");
    insertPermTemplateUser(permissionTemplate, userMarius, "admin");
    insertPermTemplateUser(permissionTemplate, userJanette, "admin");
    insertPermTemplateCharacteristic(permissionTemplate, "user", true);
    insertPermTemplateCharacteristic(permissionTemplate, "admin", true);
    PermissionTemplateDto permissionTemplate2 = insertPermissionTemplate("other template");
    insertPermTemplateCharacteristic(permissionTemplate2, "user", false);
    session.commit();
    settings.setProperty("sonar.permission.template.default", DEFAULT_TEMPLATE);

    underTest.applyDefaultPermissionTemplate(session, project2, userJanette.getId());
    session.commit();

    assertThat(dbClient.userPermissionDao().selectPermissionsByLogin(session, userMarius.getLogin(), project2.uuid())).containsOnly("admin");
    assertThat(dbClient.userPermissionDao().selectPermissionsByLogin(session, userJanette.getLogin(), project2.uuid())).containsOnly(UserRole.USER, "admin");
  }

  @Test
  public void should_add_user_permission() {
    dbTester.prepareDbUnit(getClass(), "should_add_user_permission.xml");

    underTest.insertUserPermission(PROJECT.getId(), 200L, UserRole.ADMIN, session);
    session.commit();

    dbTester.assertDbUnitTable(getClass(), "should_add_user_permission-result.xml", "user_roles", "user_id", "resource_id", "role");
    dbTester.assertDbUnitTable(getClass(), "should_add_user_permission-result.xml", "projects", "authorization_updated_at");

    checkAuthorizationUpdatedAtIsUpdated(PROJECT);
  }

  @Test
  public void should_delete_user_permission() {
    dbTester.prepareDbUnit(getClass(), "should_delete_user_permission.xml");

    underTest.deleteUserPermission(PROJECT, "dave.loper", UserRole.ADMIN, session);
    session.commit();

    dbTester.assertDbUnitTable(getClass(), "should_delete_user_permission-result.xml", "user_roles", "user_id", "resource_id", "role");
    dbTester.assertDbUnitTable(getClass(), "should_delete_user_permission-result.xml", "projects", "authorization_updated_at");
    checkAuthorizationUpdatedAtIsUpdated(PROJECT);
  }

  @Test
  public void should_insert_group_permission() {
    ComponentDto project1 = insertComponent(ComponentTesting.newProjectDto());
    ComponentDto project2 = insertComponent(ComponentTesting.newProjectDto());
    GroupDto group = insertGroup("devs");
    insertGroupRole(project1, group, "admin");

    underTest.insertGroupPermission(project2.getId(), group.getId(), UserRole.USER, session);
    session.commit();

    assertThat(dbClient.roleDao().selectGroupPermissions(session, group.getName(), project2.getId())).containsOnly(UserRole.USER);
    checkAuthorizationUpdatedAtIsUpdated(project2);
  }

  @Test
  public void should_insert_group_name_permission() {
    ComponentDto project1 = insertComponent(ComponentTesting.newProjectDto());
    ComponentDto project2 = insertComponent(ComponentTesting.newProjectDto());
    GroupDto group = insertGroup("devs");
    insertGroupRole(project1, group, "admin");

    underTest.insertGroupPermission(project2.getId(), group.getName(), UserRole.USER, session);
    session.commit();

    assertThat(dbClient.roleDao().selectGroupPermissions(session, group.getName(), project2.getId())).containsOnly(UserRole.USER);
    checkAuthorizationUpdatedAtIsUpdated(project2);
  }

  @Test
  public void should_insert_anyone_group_permission() {
    ComponentDto project1 = insertComponent(ComponentTesting.newProjectDto());
    ComponentDto project2 = insertComponent(ComponentTesting.newProjectDto());
    GroupDto group = insertGroup("devs");
    insertGroupRole(project1, group, "admin");

    underTest.insertGroupPermission(project2.getId(), "Anyone", UserRole.USER, session);
    session.commit();

    assertThat(dbClient.roleDao().selectGroupPermissions(session, "Anyone", project2.getId())).containsOnly(UserRole.USER);
    checkAuthorizationUpdatedAtIsUpdated(project2);
  }

  @Test
  public void should_delete_group_permission() {
    ComponentDto project1 = insertComponent(ComponentTesting.newProjectDto());
    GroupDto group = insertGroup("devs");
    insertGroupRole(project1, group, "admin");
    insertGroupRole(project1, group, "user");

    underTest.deleteGroupPermission(project1.getId(), group.getId(), UserRole.USER, session);
    session.commit();

    assertThat(dbClient.roleDao().selectGroupPermissions(session, group.getName(), project1.getId())).containsOnly("admin");
    checkAuthorizationUpdatedAtIsUpdated(project1);
  }

  @Test
  public void would_user_have_permission_with_default_permission_template() {
    UserDto user = userDb.insertUser();
    GroupDto group = groupDb.insertGroup();
    groupDb.addUserToGroup(user.getId(), group.getId());
    PermissionTemplateDto template = templateDb.insertTemplate();
    setDefaultTemplateUuid(template.getUuid());
    templateDb.addProjectCreatorToTemplate(template.getId(), SCAN_EXECUTION);
    templateDb.addUserToTemplate(template.getId(), user.getId(), UserRole.USER);
    templateDb.addGroupToTemplate(template.getId(), group.getId(), UserRole.CODEVIEWER);
    templateDb.addGroupToTemplate(template.getId(), null, UserRole.ISSUE_ADMIN);

    // authenticated user
    checkWouldUserHavePermission(user.getId(), UserRole.ADMIN, false);
    checkWouldUserHavePermission(user.getId(), SCAN_EXECUTION, true);
    checkWouldUserHavePermission(user.getId(), UserRole.USER, true);
    checkWouldUserHavePermission(user.getId(), UserRole.CODEVIEWER, true);
    checkWouldUserHavePermission(user.getId(), UserRole.ISSUE_ADMIN, true);

    // anonymous user
    checkWouldUserHavePermission(null, UserRole.ADMIN, false);
    checkWouldUserHavePermission(null, SCAN_EXECUTION, false);
    checkWouldUserHavePermission(null, UserRole.USER, false);
    checkWouldUserHavePermission(null, UserRole.CODEVIEWER, false);
    checkWouldUserHavePermission(null, UserRole.ISSUE_ADMIN, true);
  }

  @Test
  public void would_user_have_permission_with_unknown_default_permission_template() {
    setDefaultTemplateUuid("UNKNOWN_TEMPLATE_UUID");

    checkWouldUserHavePermission(null, UserRole.ADMIN, false);
  }

  @Test
  public void would_user_have_permission_with_empty_template() {
    PermissionTemplateDto template = templateDb.insertTemplate();
    setDefaultTemplateUuid(template.getUuid());

    checkWouldUserHavePermission(null, UserRole.ADMIN, false);
  }

  private void checkWouldUserHavePermission(@Nullable Long userId, String permission, boolean expectedResult) {
    assertThat(underTest.wouldUserHavePermissionWithDefaultTemplate(session, userId, permission, "PROJECT_KEY", Qualifiers.PROJECT)).isEqualTo(expectedResult);
  }

  private void checkAuthorizationUpdatedAtIsUpdated(ComponentDto project) {
    assertThat(dbTester.getDbClient().resourceDao().selectResource(project.getId(), session).getAuthorizationUpdatedAt()).isEqualTo(NOW);
  }

  private void setDefaultTemplateUuid(String templateUuid) {
    settings.setProperty("sonar.permission.template.default", templateUuid);
  }

  private void insertPermTemplateUser(PermissionTemplateDto permissionTemplate, UserDto user, String role) {
    dbClient.permissionTemplateDao().insertUserPermission(session, permissionTemplate.getId(), user.getId(), role);
    session.commit();
  }

  private void insertPermTemplateGroup(PermissionTemplateDto permissionTemplate, @Nullable GroupDto adminGroup, String role) {
    dbClient.permissionTemplateDao().insertGroupPermission(
      session,
      permissionTemplate.getId(),
      adminGroup == null ? null : adminGroup.getId(),
      role);
    session.commit();
  }

  private void insertPermTemplateCharacteristic(PermissionTemplateDto permissionTemplate, String role, boolean projectCreator) {
    long now = new Date().getTime();
    dbClient.permissionTemplateCharacteristicDao().insert(session,
      new PermissionTemplateCharacteristicDto()
        .setTemplateId(permissionTemplate.getId())
        .setPermission(role)
        .setWithProjectCreator(projectCreator)
        .setCreatedAt(now)
        .setUpdatedAt(now));
    session.commit();
  }

  private PermissionTemplateDto insertPermissionTemplate(String uuid) {
    PermissionTemplateDto dto = new PermissionTemplateDto().setName("default").setUuid(uuid);
    dbClient.permissionTemplateDao().insert(session, dto);
    session.commit();
    return dto;
  }

  private void insertUserRole(ComponentDto project, UserDto user, String role) {
    dbClient.userPermissionDao().insert(session, new UserPermissionDto(role, user.getId(), project.getId()));
    session.commit();
  }

  private UserDto insertUser(String login) {
    UserDto dto = UserTesting.newUserDto(login, login, null);
    dbClient.userDao().insert(session, dto);
    session.commit();
    return dto;
  }

  private void insertGroupRole(ComponentDto project, GroupDto adminGroup, String role) {
    dbClient.roleDao().insertGroupRole(session, new GroupPermissionDto().setRole(role).setGroupId(adminGroup.getId()).setResourceId(project.getId()));
    session.commit();
  }

  private ComponentDto insertComponent(ComponentDto project) {
    dbClient.componentDao().insert(session, project);
    session.commit();
    return project;
  }

  private GroupDto insertGroup(String groupName) {
    GroupDto dto = new GroupDto().setName(groupName).setOrganizationUuid(dbTester.getDefaultOrganizationUuid());
    dbClient.groupDao().insert(session, dto);
    session.commit();
    return dto;
  }

}
