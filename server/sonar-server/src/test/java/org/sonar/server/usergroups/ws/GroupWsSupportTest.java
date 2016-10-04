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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.internal.SimpleGetRequest;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationTesting;
import org.sonar.db.user.GroupDbTester;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.GroupTesting;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.organization.DefaultOrganizationProviderRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.server.usergroups.ws.GroupWsSupport.PARAM_GROUP_ID;
import static org.sonar.server.usergroups.ws.GroupWsSupport.PARAM_GROUP_NAME;
import static org.sonar.server.usergroups.ws.GroupWsSupport.PARAM_ORGANIZATION_KEY;


public class GroupWsSupportTest {

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public DefaultOrganizationProviderRule defOrgProvider = DefaultOrganizationProviderRule.create(dbTester);

  private OrganizationDto anOrg = OrganizationTesting.newOrganizationDto();
  private GroupWsSupport underTest = new GroupWsSupport(dbTester.getDbClient(), defOrgProvider);

  @Before
  public void setUp() throws Exception {
    OrganizationTesting.insert(dbTester, anOrg);
  }

  @Test
  public void findOrganizationByKey_returns_default_org_if_key_is_null() {
    OrganizationDto org = underTest.findOrganizationByKey(dbTester.getSession(), null);
    assertThat(org.getUuid()).isEqualTo(defOrgProvider.get().getUuid());
  }

  @Test
  public void findOrganizationByKey_returns_the_requested_org() {
    OrganizationDto found = underTest.findOrganizationByKey(dbTester.getSession(), anOrg.getKey());
    assertThat(found.getUuid()).isEqualTo(anOrg.getUuid());
  }

  @Test
  public void findOrganizationByKey_fails_if_org_does_not_exist() {
    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("No organization with key 'missing'");

    OrganizationDto found = underTest.findOrganizationByKey(dbTester.getSession(), "missing");
    assertThat(found.getUuid()).isEqualTo(found.getUuid());
  }

  @Test
  public void findGroup_by_id() {
    GroupDto group = GroupTesting.newGroupDto().setOrganizationUuid(anOrg.getUuid());
    new GroupDbTester(dbTester).insertGroup(group);

    SimpleGetRequest req = new SimpleGetRequest();
    req.setParam(PARAM_GROUP_ID, String.valueOf(group.getId()));

    GroupDto found = underTest.findGroup(dbTester.getSession(), req);
    assertThat(found.getId()).isEqualTo(found.getId());
  }

  @Test
  public void findGroup_by_name() {
    GroupDto group = GroupTesting.newGroupDto().setOrganizationUuid(anOrg.getUuid());
    new GroupDbTester(dbTester).insertGroup(group);

    SimpleGetRequest req = new SimpleGetRequest();
    req.setParam(PARAM_ORGANIZATION_KEY, anOrg.getKey());
    req.setParam(PARAM_GROUP_NAME, group.getName());

    GroupDto found = underTest.findGroup(dbTester.getSession(), req);
    assertThat(found.getId()).isEqualTo(found.getId());
  }

  @Test
  public void findGroup_by_name_in_default_org() {
    GroupDto group = GroupTesting.newGroupDto().setOrganizationUuid(defOrgProvider.get().getUuid());
    new GroupDbTester(dbTester).insertGroup(group);

    SimpleGetRequest req = new SimpleGetRequest();
    // missing PARAM_ORGANIZATION_KEY
    req.setParam(PARAM_GROUP_NAME, group.getName());

    GroupDto found = underTest.findGroup(dbTester.getSession(), req);
    assertThat(found.getId()).isEqualTo(found.getId());
  }

  @Test
  public void findGroup_fails_if_name_does_not_exist() {
    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("No group with name 'missing'");

    SimpleGetRequest req = new SimpleGetRequest();
    req.setParam(PARAM_GROUP_NAME, "missing");
    underTest.findGroup(dbTester.getSession(), req);
  }

  @Test
  public void findGroupOrAnyone_returns_anyone_in_default_org() {
    SimpleGetRequest req = new SimpleGetRequest();
    req.setParam(PARAM_GROUP_NAME, "anyone");
    assertThat(underTest.findGroupOrAnyone(dbTester.getSession(), req)).isNull();
  }

  @Test
  public void findGroupOrAnyone_finds_group_by_name() {
    GroupDto group = GroupTesting.newGroupDto().setOrganizationUuid(anOrg.getUuid());
    new GroupDbTester(dbTester).insertGroup(group);

    SimpleGetRequest req = new SimpleGetRequest();
    req.setParam(PARAM_ORGANIZATION_KEY, anOrg.getKey());
    req.setParam(PARAM_GROUP_NAME, group.getName());

    GroupDto found = underTest.findGroupOrAnyone(dbTester.getSession(), req);
    assertThat(found.getId()).isEqualTo(found.getId());
  }
}
