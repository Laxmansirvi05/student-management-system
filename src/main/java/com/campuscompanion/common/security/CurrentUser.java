package com.campuscompanion.common.security;

import com.campuscompanion.auth.Role;
import java.io.Serializable;

/**
 * Who is logged in. This is the only identity kept in the session: the account's internal ID and
 * its role. The email address is used once, to find the account at login, and is not stored here.
 * Services take the student from this ID, never from anything the browser sends.
 */
public record CurrentUser(Long id, Role role) implements Serializable {}
