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
package org.sonar.server.permission.ws;

import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.utils.Paging;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.permission.ExtendedUserPermissionDto;
import org.sonar.db.permission.PermissionQuery;
import org.sonar.db.user.UserDto;
import org.sonar.server.permission.ProjectId;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.WsPermissions;
import org.sonarqube.ws.WsPermissions.UsersWsResponse;

import static java.util.Collections.emptyList;
import static org.sonar.db.permission.PermissionQuery.DEFAULT_PAGE_SIZE;
import static org.sonar.db.permission.PermissionQuery.RESULTS_MAX_SIZE;
import static org.sonar.db.permission.PermissionQuery.SEARCH_QUERY_MIN_LENGTH;
import static org.sonar.server.permission.PermissionPrivilegeChecker.checkProjectAdminUserByComponentUuid;
import static org.sonar.server.permission.ws.PermissionRequestValidator.validateGlobalPermission;
import static org.sonar.server.permission.ws.PermissionRequestValidator.validateProjectPermission;
import static org.sonar.server.permission.ws.PermissionsWsParametersBuilder.createPermissionParameter;
import static org.sonar.server.permission.ws.PermissionsWsParametersBuilder.createProjectParameters;
import static org.sonar.server.ws.WsUtils.checkRequest;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PERMISSION;

public class UsersAction implements PermissionsWsAction {

  private final DbClient dbClient;
  private final UserSession userSession;
  private final PermissionWsSupport support;

  public UsersAction(DbClient dbClient, UserSession userSession, PermissionWsSupport support) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.support = support;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction("users")
      .setSince("5.2")
      .setDescription("Lists the users with their permissions as individual users rather than through group affiliation.<br>" +
        "This service defaults to global permissions, but can be limited to project permissions by providing project id or project key.<br> " +
        "This service defaults to all users, but can be limited to users with a specific permission by providing the desired permission.<br>" +
        "It requires administration permissions to access.")
      .addPagingParams(DEFAULT_PAGE_SIZE, RESULTS_MAX_SIZE)
      .setInternal(true)
      .setResponseExample(getClass().getResource("users-example.json"))
      .setHandler(this);

    action.createParam(Param.TEXT_QUERY)
      .setDescription("Limit search to user names that contain the supplied string. Must have at least %d characters.<br/>" +
        "When this parameter is not set, only users having at least one permission are returned.", SEARCH_QUERY_MIN_LENGTH)
      .setExampleValue("eri");
    createPermissionParameter(action).setRequired(false);
    createProjectParameters(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    DbSession dbSession = dbClient.openSession(false);
    try {
      Optional<ProjectId> projectId = support.findProject(dbSession, request);
      checkProjectAdminUserByComponentUuid(userSession, projectId.isPresent() ? projectId.get().getUuid() : null);

      PermissionQuery query = buildPermissionQuery(request, projectId);
      List<UserDto> users = findUsers(dbSession, query);
      int total = dbClient.userPermissionDao().countUsers(dbSession, query);
      List<ExtendedUserPermissionDto> userPermissions = findUserPermissions(dbSession, users, projectId);
      Paging paging = Paging.forPageIndex(request.mandatoryParamAsInt(Param.PAGE)).withPageSize(query.getPageSize()).andTotal(total);
      UsersWsResponse usersWsResponse = buildResponse(users, userPermissions, paging);
      writeProtobuf(usersWsResponse, request, response);
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private static PermissionQuery buildPermissionQuery(Request request, Optional<ProjectId> project) {
    String textQuery = request.param(Param.TEXT_QUERY);
    String permission = request.param(PARAM_PERMISSION);
    PermissionQuery.Builder permissionQuery = PermissionQuery.builder()
      .setPermission(permission)
      .setPageIndex(request.mandatoryParamAsInt(Param.PAGE))
      .setPageSize(request.mandatoryParamAsInt(Param.PAGE_SIZE))
      .setSearchQuery(textQuery);
    if (project.isPresent()) {
      permissionQuery.setComponentUuid(project.get().getUuid());
    }
    if (permission != null) {
      if (project.isPresent()) {
        validateProjectPermission(permission);
      } else {
        validateGlobalPermission(permission);
      }
    }
    if (textQuery == null) {
      permissionQuery.withAtLeastOnePermission();
    } else {
      checkRequest(textQuery.length() >= SEARCH_QUERY_MIN_LENGTH,
        "The '%s' parameter must have at least %d characters", Param.TEXT_QUERY, SEARCH_QUERY_MIN_LENGTH);
    }
    return permissionQuery.build();
  }

  private static UsersWsResponse buildResponse(List<UserDto> users, List<ExtendedUserPermissionDto> userPermissions, Paging paging) {
    Multimap<Long, String> permissionsByUserId = TreeMultimap.create();
    userPermissions.forEach(userPermission -> permissionsByUserId.put(userPermission.getUserId(), userPermission.getPermission()));

    UsersWsResponse.Builder response = UsersWsResponse.newBuilder();
    users.forEach(user -> {
      WsPermissions.User.Builder userResponse = response.addUsersBuilder()
        .setLogin(user.getLogin())
        .addAllPermissions(permissionsByUserId.get(user.getId()));

      if (user.getEmail() != null) {
        userResponse.setEmail(user.getEmail());
      }
      if (user.getName() != null) {
        userResponse.setName(user.getName());
      }
    });

    response.getPagingBuilder()
      .setPageIndex(paging.pageIndex())
      .setPageSize(paging.pageSize())
      .setTotal(paging.total())
      .build();

    return response.build();
  }

  private List<UserDto> findUsers(DbSession dbSession, PermissionQuery query) {
    List<String> orderedLogins = dbClient.userPermissionDao().selectLogins(dbSession, query);
    return Ordering.explicit(orderedLogins).onResultOf(UserDto::getLogin).immutableSortedCopy(dbClient.userDao().selectByLogins(dbSession, orderedLogins));
  }

  private List<ExtendedUserPermissionDto> findUserPermissions(DbSession dbSession, List<UserDto> users, Optional<ProjectId> project) {
    if (users.isEmpty()) {
      return emptyList();
    }
    List<String> logins = users.stream().map(UserDto::getLogin).collect(Collectors.toList());
    PermissionQuery query = PermissionQuery.builder()
      .setComponentUuid(project.isPresent() ? project.get().getUuid() : null)
      .withAtLeastOnePermission()
      .build();
    return dbClient.userPermissionDao().select(dbSession, query, logins);
  }
}
