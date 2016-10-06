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
import org.sonar.db.Database;
import org.sonar.db.version.BaseDataChange;

import static com.google.common.base.Preconditions.checkState;

public class PopulateOrganizationUuidOfTableGroups extends BaseDataChange {
  private static final String INTERNAL_PROPERTY_DEFAULT_ORGANIZATION = "organization.default";

  public PopulateOrganizationUuidOfTableGroups(Database db) {
    super(db);
  }

  @Override
  public void execute(Context context) throws SQLException {
    List<String> defaultOrganizationUuid = context.prepareSelect("select text_value from internal_properties where kee=?")
      .setString(1, INTERNAL_PROPERTY_DEFAULT_ORGANIZATION)
      .list(row -> row.getString(1));
    checkState(defaultOrganizationUuid.size() == 1, "Default organization uuid is missing");

    context.prepareUpsert("update groups set organization_uuid = ? where organization_uuid is null")
      .setString(1, defaultOrganizationUuid.iterator().next())
      .execute()
      .commit();
  }
}
