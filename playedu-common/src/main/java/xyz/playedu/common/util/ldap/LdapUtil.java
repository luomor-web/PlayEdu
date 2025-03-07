/*
 * Copyright (C) 2023 杭州白书科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.playedu.common.util.ldap;

import lombok.extern.slf4j.Slf4j;

import xyz.playedu.common.exception.ServiceException;
import xyz.playedu.common.util.StringUtil;

import java.util.*;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

@Slf4j
public class LdapUtil {

    private static final String USER_OBJECT_CLASS =
            "(|(objectClass=person)(objectClass=posixAccount)(objectClass=inetOrgPerson)(objectClass=organizationalPerson))";

    private static final String[] USER_RETURN_ATTRS =
            new String[] {
                "uid", // 用户的唯一识别符号，全局唯一，可以看做用户表的手机号，此字段可用于配合密码直接登录
                "cn", // CommonName -> 可以认作为人的名字，比如：张三。在LDAP中此字段是可以重复的,但是同一ou下不可重复
                "mail", // 邮箱，此值不一定存在，全局唯一，可配合密码直接登录
                "email", // 邮箱，同上
                "entryUUID",
            };
    private static final String[] OU_RETURN_ATTRS = new String[] {"ou"};

    public static LdapContext initContext(String url, String adminUser, String adminPass)
            throws NamingException {
        Hashtable<String, String> context = new Hashtable<>();
        context.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        context.put(Context.SECURITY_AUTHENTICATION, "simple");
        // 服务地址
        context.put(Context.PROVIDER_URL, url);
        // 管理员账户和密码
        context.put(Context.SECURITY_PRINCIPAL, adminUser);
        context.put(Context.SECURITY_CREDENTIALS, adminPass);
        return new InitialLdapContext(context, null);
    }

    public static List<HashMap<String, String>> users(LdapContext ldapContext, String baseDN)
            throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(USER_RETURN_ATTRS);
        controls.setReturningObjFlag(true);

        NamingEnumeration<SearchResult> result = null;
        try {
            result = ldapContext.search(baseDN, USER_OBJECT_CLASS, controls);
        } catch (NamingException e) {
            log.error("LDAP用户查询失败", e);
        } finally {
            closeContext(ldapContext);
        }

        if (result == null || !result.hasMoreElements()) {
            log.info("LDAP服务中没有用户");
            return null;
        }

        List<HashMap<String, String>> users = new ArrayList<>();
        while (result.hasMoreElements()) {
            SearchResult item = result.nextElement();
            if (item == null) {
                continue;
            }
            Attributes attributes = item.getAttributes();
            log.info("name={},attributes={}", item.getName(), attributes);
        }

        return users;
    }

    public static List<String> departments(LdapContext ldapContext, String baseDN)
            throws NamingException {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(OU_RETURN_ATTRS);
        controls.setReturningObjFlag(true);

        String filter = "(objectClass=organizationalUnit)";
        NamingEnumeration<SearchResult> result = null;
        try {
            result = ldapContext.search(baseDN, filter, controls);
        } catch (NamingException e) {
            log.error("LDAP部门查询失败", e);
        } finally {
            closeContext(ldapContext);
        }

        if (result == null || !result.hasMoreElements()) {
            log.info("LDAP部门为空");
            return null;
        }

        List<String> units = new ArrayList<>();
        while (result.hasMoreElements()) {
            SearchResult item = result.nextElement();
            if (item == null) {
                continue;
            }
            units.add(item.getName());
        }

        return units;
    }

    public static LdapTransformUser loginByMailOrUid(
            String url,
            String adminUser,
            String adminPass,
            String baseDN,
            String mail,
            String uid,
            String password)
            throws ServiceException, NamingException {
        if (StringUtil.isEmpty(mail) && StringUtil.isEmpty(uid)) {
            throw new ServiceException("mail和Uid不能同时为空");
        }

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setReturningAttributes(USER_RETURN_ATTRS);
        controls.setReturningObjFlag(true);
        controls.setCountLimit(1);

        String userFilter = "";
        if (StringUtil.isNotEmpty(mail)) {
            userFilter = String.format("(|(mail=%s)(email=%s))", mail, mail);
        } else if (StringUtil.isNotEmpty(uid)) {
            userFilter = String.format("(uid=%s)", uid);
        }

        String filter = String.format("(&%s%s)", userFilter, USER_OBJECT_CLASS);

        LdapContext ldapContext = initContext(url, adminUser, adminPass);
        NamingEnumeration<SearchResult> result = null;
        try {
            result = ldapContext.search(baseDN, filter, controls);
        } catch (NamingException e) {
            log.error("通过mail或uid登录失败", e);
        } finally {
            closeContext(ldapContext);
        }

        if (result == null || !result.hasMoreElements()) {
            log.info("用户不存在");
            return null;
        }

        // 根据mail或uid查询出来的用户
        SearchResult item = result.nextElement();
        Attributes attributes = item.getAttributes();

        String email =
                attributes.get("mail") == null ? null : (String) attributes.get("mail").get();
        if (email == null) {
            email = attributes.get("email") == null ? null : (String) attributes.get("email").get();
        }

        LdapTransformUser ldapUser = new LdapTransformUser();
        ldapUser.setDn(item.getName());
        ldapUser.setId((String) attributes.get("entryUUID").get());
        ldapUser.setCn((String) attributes.get("cn").get());
        ldapUser.setUid((String) attributes.get("uid").get());
        ldapUser.setEmail(email);

        // 使用用户dn+提交的密码去登录ldap系统
        // 登录成功则意味着密码正确
        // 登录失败则意味着密码错误
        try {
            ldapContext = initContext(url, ldapUser.getDn() + "," + baseDN, password);
            log.info("LDAP登录成功");
        } catch (Exception e) {
            // 无法登录->密码错误
            log.info("LDAP用户提交的密码错误");
            return null;
        } finally {
            ldapContext.close();
        }

        // ou计算
        String[] rdnList = ldapUser.getDn().split(",");
        List<String> ou = new ArrayList<>();
        for (String s : rdnList) {
            if (StringUtil.startsWith(s, "ou=")) {
                ou.add(s.replace("ou=", ""));
            }
        }
        Collections.reverse(ou);
        ldapUser.setOu(ou);

        return ldapUser;
    }

    public static void closeContext(LdapContext ldapCtx) {
        if (ldapCtx == null) {
            return;
        }
        try {
            ldapCtx.close();
        } catch (NamingException e) {
            log.error("Failed to close ldap context", e);
        }
    }
}
