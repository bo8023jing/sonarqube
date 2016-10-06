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
package org.sonar.db.version.v62;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;

public class PopulateOrganizationUuidOfTableGroupsTest {
  private static final String TABLE_GROUPS = "groups";

  @Rule
  public DbTester dbTester = DbTester.createForSchema(System2.INSTANCE, PopulateOrganizationUuidOfTableGroupsTest.class, "table_groups_and_internal_properties.sql");
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private PopulateOrganizationUuidOfTableGroups underTest = new PopulateOrganizationUuidOfTableGroups(dbTester.database());

  @Test
  public void execute_fails_with_ISE_if_default_organization_internal_property_is_not_set() throws SQLException {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("Default organization uuid is missing");

    underTest.execute();
  }

  @Test
  public void execute_has_no_effect_on_empty_table() throws SQLException {
    insertDefaultOrganizationInternalProperty("defOrgUuid");

    underTest.execute();

    assertThat(dbTester.countRowsOfTable(dbTester.getSession(), TABLE_GROUPS)).isEqualTo(0);
  }

  @Test
  public void execute_set_organization_uuid_of_all_rows_without_one_to_value_of_default_organization_internal_property() throws SQLException {
    insertDefaultOrganizationInternalProperty("defOrgUuid");
    dbTester.executeInsert(TABLE_GROUPS, "name", "org1");
    dbTester.executeInsert(TABLE_GROUPS, "name", "org2");
    dbTester.executeInsert(TABLE_GROUPS, "name", "org3", "organization_uuid", "already_defined");
    dbTester.commit();

    underTest.execute();

    List<Map<String, Object>> rows = dbTester.select("select organization_uuid as \"organizationUuid\", name as \"name\" from groups");
    assertThat(rows).hasSize(3);
    rows.forEach(row -> {
      String org = (String) row.get("name");
      if ("org3".equals(org)) {
        assertThat(row.get("organizationUuid")).isEqualTo("already_defined");
      } else {
        assertThat(row.get("organizationUuid")).isEqualTo("defOrgUuid");
      }
    });
  }

  @Test
  public void execute_is_reentrant() throws SQLException {
    insertDefaultOrganizationInternalProperty("defOrgUuid");
    dbTester.executeInsert(TABLE_GROUPS, "name", "org1");
    dbTester.commit();

    underTest.execute();

    underTest.execute();
  }

  private void insertDefaultOrganizationInternalProperty(String uuid) {
    dbTester.getDbClient().internalPropertiesDao().save(dbTester.getSession(), "organization.default", uuid);
    dbTester.commit();
  }

}
