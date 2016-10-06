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
import java.sql.Types;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;

public class MakeOrganizationUuidNotNullOnTableGroupsTest {
  @Rule
  public DbTester dbTester = DbTester.createForSchema(System2.INSTANCE, MakeOrganizationUuidNotNullOnTableGroupsTest.class, "table_groups.sql");

  private MakeOrganizationUuidNotNullOnTableGroups underTest = new MakeOrganizationUuidNotNullOnTableGroups(dbTester.database());

  @Test
  public void execute_makes_column_organization_uuid_not_null() throws SQLException {
    underTest.execute();

    dbTester.assertColumnDefinition("groups", "organization_uuid", Types.VARCHAR, 40, false);
  }

  @Test
  public void migration_is_reentrant() throws SQLException {
    underTest.execute();

    underTest.execute();
  }
}
