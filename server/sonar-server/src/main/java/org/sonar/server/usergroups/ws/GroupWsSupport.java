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

import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.user.UserGroupValidation;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.user.GroupDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.organization.DefaultOrganizationProvider;
import org.sonarqube.ws.WsUserGroups;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static org.sonar.api.security.DefaultGroups.isAnyone;
import static org.sonar.server.ws.WsUtils.checkFound;
import static org.sonar.server.ws.WsUtils.checkFoundWithOptional;

public class GroupWsSupport {

  static final String PARAM_GROUP_ID = "id";
  static final String PARAM_ORGANIZATION_KEY = "organizationKey";
  static final String PARAM_GROUP_NAME = "name";
  static final String PARAM_GROUP_DESCRIPTION = "description";
  static final String PARAM_LOGIN = "login";

  // Database column size should be 500 (since migration #353),
  // but on some instances, column size is still 255,
  // hence the validation is done with 255
  static final int DESCRIPTION_MAX_LENGTH = 200;

  private final DbClient dbClient;
  private final DefaultOrganizationProvider defaultOrganizationProvider;

  public GroupWsSupport(DbClient dbClient, DefaultOrganizationProvider defaultOrganizationProvider) {
    this.dbClient = dbClient;
    this.defaultOrganizationProvider = defaultOrganizationProvider;
  }

  /**
   * Find a group by its id (parameter {@link #PARAM_GROUP_ID}) or couple organization key/group name
   * (parameters {@link #PARAM_ORGANIZATION_KEY} and {@link #PARAM_GROUP_NAME}). The virtual
   * group "Anyone" is not supported.
   *
   * @throws NotFoundException if parameters are missing/incorrect or if the requested group does not exist.
   */
  public GroupDto findGroup(DbSession dbSession, Request request) {
    return findGroup(dbSession, newGroupRef(dbSession, request));
  }

  /**
   * Find a group by its id or couple organization key/group name. In the latter case, if the
   * group name is "Anyone" (case-insensitive) then {@code null} is returned.
   *
   * @return the requested group, {@code null} if Anyone
   * @throws NotFoundException if parameters are missing or if the requested group does not exist.
   */
  @CheckForNull
  public GroupDto findGroupOrAnyone(DbSession dbSession, Request request) {
    GroupRef ref = newGroupRef(dbSession, request);
    if (!ref.hasId() && isAnyone(ref.getName())) {
      return null;
    }
    return findGroup(dbSession, ref);
  }

  /**
   * Finds a user group by its reference. If organization is not defined then group
   * is searched in default organization.
   *
   * @return non-null group
   * @throws NotFoundException if the requested group does not exist
   */
  private GroupDto findGroup(DbSession dbSession, GroupRef ref) {
    if (ref.hasId()) {
      GroupDto group = dbClient.groupDao().selectById(dbSession, ref.getId());
      return checkFound(group, "No group with id '%s'", ref.getId());
    }

    Optional<GroupDto> group = dbClient.groupDao().selectByName(dbSession, ref.getOrganizationUuid(), ref.getName());
    return checkFoundWithOptional(group, "No group with name '%s'", ref.getName(), ref.getOrganizationUuid());
  }

  private GroupRef newGroupRef(DbSession dbSession, Request request) {
    Long id = request.paramAsLong(PARAM_GROUP_ID);
    String organizationKey = request.param(PARAM_ORGANIZATION_KEY);
    String name = request.param(PARAM_GROUP_NAME);

    if (id != null) {
      checkArgument(organizationKey == null && name == null, "Either id or couple organizationKey/name must be set");
      return GroupRef.fromId(id);
    }
    String organizationUuid;
    if (organizationKey == null) {
      organizationUuid = defaultOrganizationProvider.get().getUuid();
    } else {
      organizationUuid = findOrganizationByKey(dbSession, organizationKey).getUuid();
    }
    return GroupRef.fromName(organizationUuid, name);
  }

  /**
   * Loads organization from database by its key.
   * @param dbSession
   * @param key the organization key, or {@code null} to get the default organization
   * @return non-null organization
   * @throws NotFoundException if no organizations match the provided key
   */
  OrganizationDto findOrganizationByKey(DbSession dbSession, @Nullable String key) {
    String effectiveKey = key;
    if (effectiveKey == null) {
      effectiveKey = defaultOrganizationProvider.get().getKey();
    }
    Optional<OrganizationDto> org = dbClient.organizationDao().selectByKey(dbSession, effectiveKey);
    checkFoundWithOptional(org, "No organization with key '%s'", key);
    return org.get();
  }

  /**
   * Similar to {@link UserGroupValidation#validateGroupName(String)} but kept internal. No need to publish
   * this method in public API.
   * @return the same description
   */
  @CheckForNull
  String validateDescription(@Nullable String description) {
    checkArgument(description == null || description.length() <= DESCRIPTION_MAX_LENGTH,
      "Description cannot be longer than %s characters", DESCRIPTION_MAX_LENGTH);
    return description;
  }

  void checkNameDoesNotExist(DbSession dbSession, String organizationUuid, String name) {
    // There is no database constraint on column groups.name
    // because MySQL cannot create a unique index
    // on a UTF-8 VARCHAR larger than 255 characters on InnoDB
    if (dbClient.groupDao().selectByName(dbSession, organizationUuid, name).isPresent()) {
      throw new BadRequestException(format("Group '%s' already exists", name));
    }
  }

  WsUserGroups.Group.Builder toProtobuf(OrganizationDto organization, GroupDto group, int membersCount) {
    WsUserGroups.Group.Builder wsGroup = WsUserGroups.Group.newBuilder()
      .setId(group.getId())
      .setOrganizationKey(organization.getKey())
      .setName(group.getName())
      .setMembersCount(membersCount);
    if (group.getDescription() != null) {
      wsGroup.setDescription(group.getDescription());
    }
    return wsGroup;
  }

  static void defineWsGroupParameters(WebService.NewAction action) {
    defineWsGroupIdParameter(action);
    defineWsGroupNameParameter(action);
  }

  private static void defineWsGroupIdParameter(WebService.NewAction action) {
    action.createParam(PARAM_GROUP_ID)
      .setDescription("Group id")
      .setExampleValue("42");
  }

  private static void defineWsGroupNameParameter(WebService.NewAction action) {
    action.createParam(PARAM_ORGANIZATION_KEY)
      .setDescription("Key of organization")
      .setExampleValue("my-org")
      .setSince("6.2");
    action.createParam(PARAM_GROUP_NAME)
      .setDescription("Group name")
      .setExampleValue("sonar-administrators");
  }

  static WebService.NewParam defineWsLoginParameter(WebService.NewAction action) {
    return action.createParam(PARAM_LOGIN)
      .setDescription("User login")
      .setExampleValue("g.hopper");
  }
}
