/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.component.ws;

import com.google.common.io.Resources;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.component.SnapshotDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.DbTester;
import org.sonar.server.component.ComponentTesting;
import org.sonar.server.component.SnapshotTesting;
import org.sonar.server.component.db.ComponentDao;
import org.sonar.server.component.db.SnapshotDao;
import org.sonar.server.db.DbClient;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsTester;
import org.sonar.test.JsonAssert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ProjectsProvisionedActionTest {

  @ClassRule
  public static DbTester db = new DbTester();
  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();

  WsTester ws;
  DbClient dbClient;
  DbSession dbSession;
  ComponentDao componentDao;
  System2 system2 = mock(System2.class);

  @After
  public void tearDown() {
    dbSession.close();
  }

  @Before
  public void setUp() {
    dbClient = new DbClient(db.database(), db.myBatis(), new ComponentDao(), new SnapshotDao(System2.INSTANCE));
    dbSession = dbClient.openSession(false);
    componentDao = dbClient.componentDao();
    db.truncateTables();
    ws = new WsTester(new ProjectsWs(new ProjectsProvisionedAction(dbClient, userSessionRule)));
  }

  @Test
  public void all_provisioned_projects_without_analyzed_projects() throws Exception {
    userSessionRule.setGlobalPermissions(UserRole.ADMIN);
    ComponentDto analyzedProject = ComponentTesting.newProjectDto("analyzed-uuid-1");
    componentDao.insert(dbSession, newProvisionedProject("1"), newProvisionedProject("2"), analyzedProject);
    SnapshotDto snapshot = SnapshotTesting.createForProject(analyzedProject);
    dbClient.snapshotDao().insert(dbSession, snapshot);
    dbSession.commit();

    WsTester.Result result = ws.newGetRequest("api/projects", "provisioned").execute();

    result.assertJson(getClass(), "all-projects.json");
    assertThat(result.outputAsString()).doesNotContain("analyzed-uuid-1");
  }

  @Test
  public void provisioned_projects_with_correct_pagination() throws Exception {
    userSessionRule.setGlobalPermissions(UserRole.ADMIN);
    for (int i = 1; i <= 10; i++) {
      componentDao.insert(dbSession, newProvisionedProject(String.valueOf(i)));
    }
    dbSession.commit();

    WsTester.TestRequest request = ws.newGetRequest("api/projects", "provisioned")
      .setParam(Param.PAGE, "3")
      .setParam(Param.PAGE_SIZE, "4");

    String jsonOutput = request.execute().outputAsString();

    assertThat(StringUtils.countMatches(jsonOutput, "provisioned-uuid-")).isEqualTo(2);
  }

  @Test
  public void provisioned_projects_with_desired_fields() throws Exception {
    userSessionRule.setGlobalPermissions(UserRole.ADMIN);
    componentDao.insert(dbSession, newProvisionedProject("1"));
    dbSession.commit();

    String jsonOutput = ws.newGetRequest("api/projects", "provisioned")
      .setParam(Param.FIELDS, "key")
      .execute().outputAsString();

    assertThat(jsonOutput).contains("uuid", "key")
      .doesNotContain("name")
      .doesNotContain("creationDate");
  }

  @Test
  public void provisioned_projects_with_query() throws Exception {
    userSessionRule.setGlobalPermissions(UserRole.ADMIN);
    componentDao.insert(dbSession, newProvisionedProject("1"), newProvisionedProject("2"));
    dbSession.commit();

    String jsonOutput = ws.newGetRequest("api/projects", "provisioned")
      .setParam(Param.TEXT_QUERY, "PROVISIONED-name-2")
      .execute().outputAsString();

    assertThat(jsonOutput)
      .contains("provisioned-name-2", "provisioned-uuid-2")
      .doesNotContain("provisioned-uuid-1");
    assertThat(componentDao.countProvisionedProjects(dbSession, "name-2")).isEqualTo(1);
    assertThat(componentDao.countProvisionedProjects(dbSession, "key-2")).isEqualTo(1);
    assertThat(componentDao.countProvisionedProjects(dbSession, "visioned-name-")).isEqualTo(2);
  }

  private static ComponentDto newProvisionedProject(String uuid) {
    return ComponentTesting
      .newProjectDto("provisioned-uuid-" + uuid)
      .setName("provisioned-name-" + uuid)
      .setKey("provisioned-key-" + uuid);
  }

  @Test
  public void provisioned_projects_as_defined_in_the_example() throws Exception {
    userSessionRule.setGlobalPermissions(UserRole.ADMIN);
    ComponentDto hBaseProject = ComponentTesting.newProjectDto("ce4c03d6-430f-40a9-b777-ad877c00aa4d")
      .setKey("org.apache.hbas:hbase")
      .setName("HBase")
      .setCreatedAt(DateUtils.parseDateTime("2015-03-04T23:03:44+0100"));
    ComponentDto roslynProject = ComponentTesting.newProjectDto("c526ef20-131b-4486-9357-063fa64b5079")
      .setKey("com.microsoft.roslyn:roslyn")
      .setName("Roslyn")
      .setCreatedAt(DateUtils.parseDateTime("2013-03-04T23:03:44+0100"));
    componentDao.insert(dbSession, hBaseProject, roslynProject);
    dbSession.commit();

    WsTester.Result result = ws.newGetRequest("api/projects", "provisioned").execute();

    JsonAssert.assertJson(result.outputAsString()).isSimilarTo(Resources.getResource(getClass(), "projects-example-provisioned.json"));
  }
}