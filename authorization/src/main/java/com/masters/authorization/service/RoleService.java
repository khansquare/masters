package com.masters.authorization.service;

import java.util.List;

import com.masters.authorization.model.Role;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;

public interface RoleService {
	int insertRole(Role role) throws MySQLIntegrityConstraintViolationException;
	Role getRole(String title);
	Role getRole(int roleId);
	void updateRole(Role role);
	List<Role> getAllRoles();
}