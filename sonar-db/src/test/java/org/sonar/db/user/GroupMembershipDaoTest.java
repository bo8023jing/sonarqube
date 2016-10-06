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

import com.google.common.collect.Multimap;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.sonar.db.user.UserMembershipQuery.IN;
import static org.sonar.db.user.UserMembershipQuery.OUT;


public class GroupMembershipDaoTest {

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);

  private DbClient dbClient = dbTester.getDbClient();
  private DbSession dbSession = dbTester.getSession();
  private GroupMembershipDao underTest = dbTester.getDbClient().groupMembershipDao();

  private GroupDto groupAdmin;
  private GroupDto groupUser;
  private GroupDto groupReviewer;
  private UserDto userAdmin;
  private UserDto userActive;

  @Before
  public void setUp() throws Exception {
    groupAdmin = insertGroup("sonar-administrators");
    groupUser = insertGroup("sonar-users");
    groupReviewer = insertGroup("sonar-reviewers");
    userAdmin = insertUser("admin");
    userActive = insertUser("notAdmin");
    insertUser("inactive", false);

    addUserToGroup(groupAdmin, userAdmin);
    addUserToGroup(groupUser, userAdmin);
    addUserToGroup(groupReviewer, userAdmin);
    addUserToGroup(groupUser, userActive);
  }

  @Test
  public void count_groups() {
    // 200 is member of 3 groups
    assertThat(underTest.countGroups(dbSession, GroupMembershipQuery.builder().login("arthur").membership(GroupMembershipQuery.IN).build(), userAdmin.getId())).isEqualTo(3);
    assertThat(underTest.countGroups(dbSession, GroupMembershipQuery.builder().login("arthur").membership(GroupMembershipQuery.OUT).build(), userAdmin.getId())).isZero();
    // 201 is member of 1 group on 3
    assertThat(underTest.countGroups(dbSession, GroupMembershipQuery.builder().login("arthur").membership(GroupMembershipQuery.IN).build(), userActive.getId())).isEqualTo(1);
    assertThat(underTest.countGroups(dbSession, GroupMembershipQuery.builder().login("arthur").membership(GroupMembershipQuery.OUT).build(), userActive.getId())).isEqualTo(2);
    // 999 is member of 0 group
    assertThat(underTest.countGroups(dbSession, GroupMembershipQuery.builder().login("arthur").membership(GroupMembershipQuery.IN).build(), 999L)).isZero();
    assertThat(underTest.countGroups(dbSession, GroupMembershipQuery.builder().login("arthur").membership(GroupMembershipQuery.OUT).build(), 2999L)).isEqualTo(3);
  }

  @Test
  public void count_users_by_group() {
    GroupDto groupEmpty = insertGroup("sonar-nobody");

    assertThat(underTest.countUsersByGroups(dbSession, Arrays.asList(groupAdmin.getId(), groupUser.getId(), groupReviewer.getId(), groupEmpty.getId()))).containsOnly(
      entry("sonar-users", 2), entry("sonar-reviewers", 1), entry("sonar-administrators", 1), entry("sonar-nobody", 0));
    assertThat(underTest.countUsersByGroups(dbSession, Arrays.asList(groupAdmin.getId(), groupEmpty.getId()))).containsOnly(
      entry("sonar-administrators", 1), entry("sonar-nobody", 0));
  }

  @Test
  public void count_groups_by_login() {
    UserDto user2 = insertUser("user2");

    assertThat(underTest.selectGroupsByLogins(dbSession, Arrays.asList()).keys()).isEmpty();
    Multimap<String, String> groupsByLogin = underTest.selectGroupsByLogins(dbSession, Arrays.asList(userAdmin.getLogin(), userActive.getLogin(), user2.getLogin()));
    assertThat(groupsByLogin.get(userAdmin.getLogin())).containsExactly("sonar-administrators", "sonar-reviewers", "sonar-users");
    assertThat(groupsByLogin.get(userActive.getLogin())).containsExactly("sonar-users");
    assertThat(groupsByLogin.get(user2.getLogin())).isEmpty();
  }

  @Test
  public void count_members() {
    GroupDto groupEmpty = insertGroup("sonar-nobody");

    // 100 has 1 member and 1 non member
    assertThat(underTest.countMembers(dbSession, newMembershipQuery(groupAdmin, IN))).isEqualTo(1);
    assertThat(underTest.countMembers(dbSession, newMembershipQuery(groupAdmin, OUT))).isEqualTo(1);
    // 101 has 2 members
    assertThat(underTest.countMembers(dbSession, newMembershipQuery(groupUser, IN))).isEqualTo(2);
    assertThat(underTest.countMembers(dbSession, newMembershipQuery(groupUser, OUT))).isZero();
    // 102 has 1 member and 1 non member
    assertThat(underTest.countMembers(dbSession, newMembershipQuery(groupReviewer, IN))).isEqualTo(1);
    assertThat(underTest.countMembers(dbSession, newMembershipQuery(groupReviewer, OUT))).isEqualTo(1);
    // 103 has no member
    assertThat(underTest.countMembers(dbSession, newMembershipQuery(groupEmpty, IN))).isZero();
    assertThat(underTest.countMembers(dbSession, newMembershipQuery(groupEmpty, OUT))).isEqualTo(2);
  }

  @Test
  public void select_group_members_by_query() {
    GroupDto groupEmpty = insertGroup("sonar-nobody");

    // 100 has 1 member
    assertThat(underTest.selectMembers(dbSession, newMembershipQuery(groupAdmin, IN), 0, 10)).hasSize(1);
    // 101 has 2 members
    assertThat(underTest.selectMembers(dbSession, newMembershipQuery(groupUser, IN), 0, 10)).hasSize(2);
    // 102 has 1 member
    assertThat(underTest.selectMembers(dbSession, newMembershipQuery(groupReviewer, IN), 0, 10)).hasSize(1);
    // 103 has no member
    assertThat(underTest.selectMembers(dbSession, newMembershipQuery(groupEmpty, IN), 0, 10)).isEmpty();
  }

  @Test
  public void select_users_not_affected_to_a_group_by_query() {
    GroupDto groupEmpty = insertGroup("sonar-nobody");

    // 100 has 1 member
    assertThat(underTest.selectMembers(dbSession, newMembershipQuery(groupAdmin, OUT), 0, 10)).hasSize(1);
    // 101 has 2 members
    assertThat(underTest.selectMembers(dbSession, newMembershipQuery(groupUser, OUT), 0, 10)).isEmpty();
    // 102 has 1 member
    assertThat(underTest.selectMembers(dbSession, newMembershipQuery(groupReviewer, OUT), 0, 10)).hasSize(1);
    // 103 has no member
    assertThat(underTest.selectMembers(dbSession, newMembershipQuery(groupEmpty, OUT), 0, 10)).hasSize(2);
  }

  @Test
  public void search_by_user_name_or_login() {
    insertGroup("sonar-nobody");

    List<UserMembershipDto> result = underTest.selectMembers(dbSession, UserMembershipQuery.builder().groupId(groupAdmin.getId()).memberSearch("admin").build(), 0, 10);
    assertThat(result).hasSize(2);

    assertThat(result.get(0).getLogin()).isEqualTo("admin");
    assertThat(result.get(1).getLogin()).isEqualTo("notAdmin");

    result = underTest.selectMembers(dbSession, UserMembershipQuery.builder().groupId(groupAdmin.getId()).memberSearch("not").build(), 0, 10);
    assertThat(result).hasSize(1);
  }

  @Test
  public void search_by_login_or_name_with_capitalization() {
    insertGroup("sonar-nobody");

    List<UserMembershipDto> result = underTest.selectMembers(dbSession, UserMembershipQuery.builder().groupId(100L).memberSearch("admin").build(), 0, 10);
    assertThat(result).hasSize(2);

    result = underTest.selectMembers(dbSession, UserMembershipQuery.builder().groupId(100L).memberSearch("AdMiN").build(), 0, 10);
    assertThat(result).hasSize(2);

  }

  @Test
  public void should_be_sorted_by_user_name() {
    insertGroup("sonar-nobody");

    List<UserMembershipDto> result = underTest.selectMembers(dbSession, UserMembershipQuery.builder().groupId(groupAdmin.getId()).build(), 0, 10);
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getLogin()).isEqualTo(userAdmin.getLogin());
    assertThat(result.get(1).getLogin()).isEqualTo(userActive.getLogin());
  }

  @Test
  public void members_should_be_paginated() {
    insertGroup("sonar-nobody");

    List<UserMembershipDto> result = underTest.selectMembers(dbSession, UserMembershipQuery.builder().groupId(groupAdmin.getId()).build(), 0, 2);
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getLogin()).isEqualTo(userAdmin.getLogin());
    assertThat(result.get(1).getLogin()).isEqualTo(userActive.getLogin());

    result = underTest.selectMembers(dbSession, UserMembershipQuery.builder().groupId(groupAdmin.getId()).build(), 1, 2);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getLogin()).isEqualTo(userActive.getLogin());

    result = underTest.selectMembers(dbSession, UserMembershipQuery.builder().groupId(groupAdmin.getId()).build(), 2, 1);
    assertThat(result).isEmpty();
  }

  private GroupDto insertGroup(String groupName) {
    GroupDto dto = new GroupDto().setName(groupName).setOrganizationUuid(dbTester.getDefaultOrganizationUuid());
    dbClient.groupDao().insert(dbSession, dto);
    dbSession.commit();
    return dto;
  }

  private UserDto insertUser(String login) {
    return insertUser(login, true);
  }

  private UserDto insertUser(String login, boolean active) {
    UserDto dto = UserTesting.newUserDto(login, login, null).setActive(active);
    dbClient.userDao().insert(dbSession, dto);
    dbSession.commit();
    return dto;
  }

  private void addUserToGroup(GroupDto groupAdmin, UserDto user1) {
    dbClient.userGroupDao().insert(dbSession, new UserGroupDto().setUserId(user1.getId()).setGroupId(groupAdmin.getId()));
    dbSession.commit();
  }

  private static UserMembershipQuery newMembershipQuery(GroupDto groupEmpty, String membership) {
    return UserMembershipQuery.builder().groupId(groupEmpty.getId()).membership(membership).build();
  }
}
