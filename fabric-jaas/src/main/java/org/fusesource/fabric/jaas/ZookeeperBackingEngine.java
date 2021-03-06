/**
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.jaas;

import org.apache.felix.utils.properties.Properties;
import org.apache.karaf.jaas.modules.*;
import org.apache.karaf.jaas.modules.UserPrincipal;
import org.apache.karaf.jaas.modules.encryption.EncryptionSupport;
import org.apache.karaf.jaas.modules.properties.PropertiesBackingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ZookeeperBackingEngine implements BackingEngine {

    public static final String USERS_NODE = "fabric/authentication/users";

    private static final transient Logger LOGGER = LoggerFactory.getLogger(PropertiesBackingEngine.class);

    private Properties users;
    private EncryptionSupport encryptionSupport;

    /**
     * Constructor
     *
     * @param users
     */
    public ZookeeperBackingEngine(Properties users) {
        this.users = users;
    }

    public ZookeeperBackingEngine(Properties users, EncryptionSupport encryptionSupport) {
        this.users = users;
        this.encryptionSupport = encryptionSupport;
    }

    /**
     * Add a user.
     *
     * @param username
     * @param password
     */
    public void addUser(String username, String password) {
        String[] infos = null;
        StringBuffer userInfoBuffer = new StringBuffer();

        String newPassword = password;

        //If encryption support is enabled, encrypt password
        if (encryptionSupport != null && encryptionSupport.getEncryption() != null) {
            newPassword = encryptionSupport.getEncryption().encryptPassword(password);
            if (encryptionSupport.getEncryptionPrefix() != null) {
                newPassword = encryptionSupport.getEncryptionPrefix() + newPassword;
            }
            if (encryptionSupport.getEncryptionSuffix() != null) {
                newPassword = newPassword + encryptionSupport.getEncryptionSuffix();
            }
        }

        String userInfos = (String) users.get(username);

        //If user already exists, update password
        if (userInfos != null && userInfos.length() > 0) {
            infos = userInfos.split(",");
            userInfoBuffer.append(newPassword);

            for (int i = 1; i < infos.length; i++) {
                userInfoBuffer.append(",");
                userInfoBuffer.append(infos[i]);
            }
            String newUserInfo = userInfoBuffer.toString();
            users.put(username, newUserInfo);
        } else {
            users.put(username, newPassword);
        }

        try {
            users.save();
        } catch (Exception ex) {
            LOGGER.error("Cannot update users file,", ex);
        }
    }

    /**
     * Delete a User.
     *
     * @param username
     */
    public void deleteUser(String username) {
        users.remove(username);
        try {
            users.save();
        } catch (Exception ex) {
            LOGGER.error("Cannot remove users file,", ex);
        }
    }

    /**
     * List Users
     *
     * @return
     */
    public List<UserPrincipal> listUsers() {
        List<UserPrincipal> result = new ArrayList<UserPrincipal>();

        for (String userNames :	(Set<String>) users.keySet()) {
            UserPrincipal userPrincipal = new UserPrincipal(userNames);
            result.add(userPrincipal);
        }
        return result;
    }

    /**
     * List the Roles of the {@param user}
     *
     * @param user
     * @return
     */
    public List<RolePrincipal> listRoles(UserPrincipal user) {
        List<RolePrincipal> result = new ArrayList<RolePrincipal>();
        String userInfo = (String) users.get(user.getName());
        String[] infos = userInfo.split(",");
        for (int i = 1; i < infos.length; i++) {
            result.add(new RolePrincipal(infos[i]));
        }
        return result;
    }

    /**
     * Add a role to a User.
     *
     * @param username
     * @param role
     */
    public void addRole(String username, String role) {
        String userInfos = (String) users.get(username);
        if (userInfos != null) {
            String newUserInfos = userInfos + "," + role;
            users.put(username, newUserInfos);
        }
        try {
            users.save();
        } catch (Exception ex) {
            LOGGER.error("Cannot update users file,", ex);
        }
    }

    /**
     * Delete a Role form User.
     *
     * @param username
     * @param role
     */
    public void deleteRole(String username, String role) {
        String[] infos = null;
        StringBuffer userInfoBuffer = new StringBuffer();

        String userInfos = (String) users.get(username);

        //If user already exists, remove the role
        if (userInfos != null && userInfos.length() > 0) {
            infos = userInfos.split(",");
            String password = infos[0];
            userInfoBuffer.append(password);

            for (int i = 1; i < infos.length; i++) {
                if (infos[i] != null && !infos[i].equals(role)) {
                    userInfoBuffer.append(",");
                    userInfoBuffer.append(infos[i]);
                }
            }
            String newUserInfo = userInfoBuffer.toString();
            users.put(username, newUserInfo);
        }

        try {
            users.save();
        } catch (Exception ex) {
            LOGGER.error("Cannot update users file,", ex);
        }
    }
}
