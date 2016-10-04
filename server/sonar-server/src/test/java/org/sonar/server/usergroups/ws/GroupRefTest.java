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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.server.usergroups.ws.GroupRef.fromName;

public class GroupRefTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void test_ref_by_id() {
    GroupRef ref = GroupRef.fromId(10L);
    assertThat(ref.hasId()).isTrue();
    assertThat(ref.getId()).isEqualTo(10L);
  }

  @Test
  public void test_ref_by_name() {
    GroupRef ref = fromName("ORG1", "the-group");
    assertThat(ref.hasId()).isFalse();
    assertThat(ref.getOrganizationUuid()).isEqualTo("ORG1");
    assertThat(ref.getName()).isEqualTo("the-group");
  }

  @Test
  public void test_equals_and_hashCode() {
    GroupRef refId1 = GroupRef.fromId(10L);
    GroupRef refId2 = GroupRef.fromId(11L);
    assertThat(refId1.equals(refId1)).isTrue();
    assertThat(refId1.equals(GroupRef.fromId(10L))).isTrue();
    assertThat(refId1.hashCode()).isEqualTo(GroupRef.fromId(10L).hashCode());
    assertThat(refId1.equals(refId2)).isFalse();

    GroupRef refName1 = fromName("ORG1", "the-group");
    GroupRef refName2 = fromName("ORG1", "the-group2");
    GroupRef refName3 = fromName("ORG2", "the-group2");
    assertThat(refName1.equals(refName1)).isTrue();
    assertThat(refName1.equals(fromName("ORG1", "the-group"))).isTrue();
    assertThat(refName1.hashCode()).isEqualTo(fromName("ORG1", "the-group").hashCode());
    assertThat(refName1.equals(refName2)).isFalse();
    assertThat(refName2.equals(refName3)).isFalse();
  }

  @Test
  public void test_toString() {
    GroupRef refId = GroupRef.fromId(10L);
    assertThat(refId.toString()).isEqualTo("GroupRef{id=10, organizationUuid='null', name='null'}");
  }
}
