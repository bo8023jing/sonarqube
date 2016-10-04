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
package org.sonar.server.usergroups.ws;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserGroupDto;
import org.sonar.db.user.UserTesting;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.ServerException;
import org.sonar.server.organization.DefaultOrganizationProviderRule;
import org.sonar.server.platform.PersistentSettings;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsTester;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonar.db.user.GroupTesting.newGroupDto;

public class UpdateActionTest {

  static final String DEFAULT_GROUP_NAME_KEY = "sonar.defaultGroup";
  static final String DEFAULT_GROUP_NAME_VALUE = "DEFAULT_GROUP_NAME_VALUE";

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public DefaultOrganizationProviderRule defaultOrganizationProvider = DefaultOrganizationProviderRule.create(db);

  private DbClient dbClient = db.getDbClient();
  private DbSession dbSession = db.getSession();
  private PersistentSettings settings = mock(PersistentSettings.class);
  private WsTester ws;

  @Before
  public void setUp() throws Exception {
    GroupWsSupport groupSupport = new GroupWsSupport(db.getDbClient(), defaultOrganizationProvider);
    ws = new WsTester(new UserGroupsWs(new UpdateAction(dbClient, userSession, groupSupport, settings)));
    when(settings.getString(DEFAULT_GROUP_NAME_KEY)).thenReturn(DEFAULT_GROUP_NAME_VALUE);
  }

  @Test
  public void update_both_name_and_description() throws Exception {
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName("old-name").setDescription("Old Description"));
    UserDto user = dbClient.userDao().insert(dbSession, UserTesting.newUserDto());
    dbClient.userGroupDao().insert(dbSession, new UserGroupDto().setGroupId(existingGroup.getId()).setUserId(user.getId()));
    dbSession.commit();

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("name", "new-name")
      .setParam("description", "New Description")
      .execute().assertJson("{" +
        "  \"group\": {" +
        "    \"name\": \"new-name\"," +
        "    \"description\": \"New Description\"," +
        "    \"membersCount\": 1" +
        "  }" +
        "}");
  }

  @Test
  public void update_only_name() throws Exception {
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName("old-name").setDescription("Old Description"));
    dbSession.commit();

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("name", "new-name")
      .execute().assertJson("{" +
        "  \"group\": {" +
        "    \"name\": \"new-name\"," +
        "    \"description\": \"Old Description\"," +
        "    \"membersCount\": 0" +
        "  }" +
        "}");
  }

  @Test
  public void update_only_description() throws Exception {
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName("old-name").setDescription("Old Description"));
    dbSession.commit();

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("description", "New Description")
      .execute().assertJson("{" +
        "  \"group\": {" +
        "    \"name\": \"old-name\"," +
        "    \"description\": \"New Description\"," +
        "    \"membersCount\": 0" +
        "  }" +
        "}");
  }

  @Test
  public void update_default_group_name_also_update_default_group_property() throws Exception {
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName(DEFAULT_GROUP_NAME_VALUE).setDescription("Default group name"));
    dbSession.commit();

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("name", "new-name")
      .execute();

    verify(settings).saveProperty(any(DbSession.class), eq(DEFAULT_GROUP_NAME_KEY), eq("new-name"));
  }

  @Test
  public void update_default_group_name_does_not_update_default_group_setting_when_null() throws Exception {
    when(settings.getString(DEFAULT_GROUP_NAME_KEY)).thenReturn(null);
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName(DEFAULT_GROUP_NAME_VALUE).setDescription("Default group name"));
    dbSession.commit();

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("name", "new-name")
      .execute();

    verify(settings, never()).saveProperty(any(DbSession.class), eq(DEFAULT_GROUP_NAME_KEY), eq("new-name"));
  }

  @Test
  public void require_admin_permission() throws Exception {
    expectedException.expect(ForbiddenException.class);

    userSession.login("not-admin");
    newRequest()
      .setParam("id", "42")
      .setParam("name", "some-product-bu")
      .setParam("description", "Business Unit for Some Awesome Product")
      .execute();
  }

  @Test
  public void fail_if_name_is_too_short() throws Exception {
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName("old-name").setDescription("Old Description"));

    expectedException.expect(IllegalArgumentException.class);

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("name", "")
      .execute();
  }

  @Test
  public void fail_if_name_is_too_long() throws Exception {
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName("old-name").setDescription("Old Description"));

    expectedException.expect(IllegalArgumentException.class);

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("name", StringUtils.repeat("a", 255 + 1))
      .execute();
  }

  @Test
  public void forbidden_name() throws Exception {
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName("old-name").setDescription("Old Description"));

    expectedException.expect(IllegalArgumentException.class);

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("name", "AnYoNe")
      .execute();
  }

  @Test
  public void fail_to_update_if_name_already_exists() throws Exception {
    GroupDto groupToBeRenamed = insertGroupInDefaultOrganization(newGroupDto().setName("old-name"));
    String newName = "new-name";
    insertGroupInDefaultOrganization(newGroupDto().setName(newName));

    expectedException.expect(ServerException.class);
    expectedException.expectMessage("Group 'new-name' already exists");

    loginAsAdmin();
    newRequest()
      .setParam("id", groupToBeRenamed.getId().toString())
      .setParam("name", newName)
      .execute();
  }

  @Test
  public void description_too_long() throws Exception {
    GroupDto existingGroup = insertGroupInDefaultOrganization(newGroupDto().setName("old-name").setDescription("Old Description"));
    dbSession.commit();

    expectedException.expect(IllegalArgumentException.class);

    loginAsAdmin();
    newRequest()
      .setParam("id", existingGroup.getId().toString())
      .setParam("name", "long-group-description-is-looooooooooooong")
      .setParam("description", StringUtils.repeat("a", 200 + 1))
      .execute();
  }

  @Test
  public void unknown_group() throws Exception {
    expectedException.expect(NotFoundException.class);

    loginAsAdmin();
    newRequest()
      .setParam("id", "42")
      .execute();
  }

  private WsTester.TestRequest newRequest() {
    return ws.newPostRequest("api/user_groups", "update");
  }

  private void loginAsAdmin() {
    userSession.login("admin").setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
  }

  private GroupDto insertGroupInDefaultOrganization(GroupDto group) {
    group.setOrganizationUuid(defaultOrganizationProvider.get().getUuid());
    dbClient.groupDao().insert(dbSession, group);
    dbSession.commit();
    return group;
  }
}
