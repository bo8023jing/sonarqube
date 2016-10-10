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

import javax.annotation.Nullable;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;

public class PermissionDbTester {
  private final DbTester db;
  private final DbClient dbClient;
  private final DbSession dbSession;

  public PermissionDbTester(DbTester db) {
    this.db = db;
    this.dbClient = db.getDbClient();
    this.dbSession = db.getSession();
  }

  public void addProjectPermissionToGroup(String permission, @Nullable Long groupId, long componentId) {
    dbClient.groupPermissionDao().insert(dbSession, new GroupPermissionDto()
      .setRole(permission)
      .setGroupId(groupId)
      .setResourceId(componentId));
    db.commit();
  }

  public void addGlobalPermissionToGroup(String permission, @Nullable Long groupId) {
    dbClient.groupPermissionDao().insert(dbSession, new GroupPermissionDto()
      .setRole(permission)
      .setGroupId(groupId));
    db.commit();
  }
}
