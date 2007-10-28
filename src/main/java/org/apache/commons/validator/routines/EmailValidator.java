/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.validator.routines;

import org.apache.oro.text.perl.Perl5Util;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Perform email validations.</p>
 * <p>
 * This class is a Singleton; you can retrieve the instance via the getInstance() method.
 * </p>
 * <p>
 * Based on a script by <a href="mailto:stamhankar@hotmail.com">Sandeep V. Tamhankar</a>
 * http://javascript.internet.com
 * </p>
 * <p>
 * This implementation is not guaranteed to catch all possible errors in an email address.
 * For example, an address like nobody@noplace.somedog will pass validator, even though there
 * is no TLD "somedog"
 * </p>.
 *
 * @version $Revision$ $Date$
 * @since Validator 1.4
 */
public class EmailValidator implements Serializable {

    private static final String SPECIAL_CHARS = "\\p{Cntrl}\\(\\)<>@,;:'\\\\\\\"\\.\\[\\]";
    private static final String VALID_CHARS = "[^\\s" + SPECIAL_CHARS + "]";
    private static final String QUOTED_USER = "(\"[^\"]*\")";
    private static final String ATOM = VALID_CHARS + '+';
    private static final String WORD = "((" + VALID_CHARS + "|')+|" + QUOTED_USER + ")";

    private static final String LEGAL_ASCII_PATTERN = "^\\p{ASCII}+$";
    private static final String EMAIL_PATTERN = "^(.+)@(.+)$";
    private static final String IP_DOMAIN_PATTERN = "^\\[(.*)\\]$";
    private static final String TLD_PATTERN = "^\\p{Alpha}+$";

    private static final String USER_PATTERN = "^\\s*" + WORD + "(\\." + WORD + ")*$";
    private static final String DOMAIN_PATTERN = "^" + ATOM + "(\\." + ATOM + ")*\\s*$";
    private static final String ATOM_PATTERN = "(" + ATOM + ")";

    /**
     * Singleton instance of this class.
     */
    private static final EmailValidator EMAIL_VALIDATOR = new EmailValidator();

    /**
     * Returns the Singleton instance of this validator.
     *
     * @return singleton instance of this validator.
     */
    public static EmailValidator getInstance() {
        return EMAIL_VALIDATOR;
    }

    /**                                       l
     * Protected constructor for subclasses to use.
     */
    protected EmailValidator() {
        super();
    }

    /**
     * <p>Checks if a field has a valid e-mail address.</p>
     *
     * @param email The value validation is being performed on.  A <code>null</code>
     *              value is considered invalid.
     * @return true if the email address is valid.
     */
    public boolean isValid(String email) {
        if (email == null) {
            return false;
        }

        Pattern matchAsciiPattern = Pattern.compile(LEGAL_ASCII_PATTERN);
        Matcher asciiMatcher = matchAsciiPattern.matcher(email);
        if (!asciiMatcher.matches()) {
            return false;
        }

        email = stripComments(email);

        // Check the whole email address structure
        Pattern emailPattern = Pattern.compile(EMAIL_PATTERN);
        Matcher emailMatcher = emailPattern.matcher(email);
        if (!emailMatcher.matches()) {
            return false;
        }

        if (email.endsWith(".")) {
            return false;
        }

        if (!isValidUser(emailMatcher.group(1))) {
            return false;
        }

        if (!isValidDomain(emailMatcher.group(2))) {
            return false;
        }

        return true;
    }

    /**
     * Returns true if the domain component of an email address is valid.
     *
     * @param domain being validated.
     * @return true if the email address's domain is valid.
     */
    protected boolean isValidDomain(String domain) {
        boolean symbolic = false;

        // see if domain is an IP address in brackets
        Pattern ipDomainPattern = Pattern.compile(IP_DOMAIN_PATTERN);
        Matcher ipDomainMatcher = ipDomainPattern.matcher(domain);

        if (ipDomainMatcher.matches()) {
            InetAddressValidator inetAddressValidator =
                    InetAddressValidator.getInstance();
            if (inetAddressValidator.isValid(ipDomainMatcher.group(1))) {
                return true;
            }
        } else {
            // Domain is symbolic name
            symbolic = Pattern.matches(DOMAIN_PATTERN, domain);
        }

        if (symbolic) {
            if (!isValidSymbolicDomain(domain)) {
                return false;
            }
        } else {
            return false;
        }

        return true;
    }

    /**
     * Returns true if the user component of an email address is valid.
     *
     * @param user being validated
     * @return true if the user name is valid.
     */
    protected boolean isValidUser(String user) {
        return Pattern.matches(USER_PATTERN, user);
    }

    /**
     * Validates a symbolic domain name.  Returns true if it's valid.
     *
     * @param domain symbolic domain name
     * @return true if the symbolic domain name is valid.
     */
    protected boolean isValidSymbolicDomain(String domain) {
        String[] domainSegment = new String[10];
        boolean match = true;
        int i = 0;

        // Iterate through the domain, checking that it's composed
        // of valid atoms in between the dots.
        // FIXME: This should be cleaned up some more; it's still a bit dodgy.
        Pattern atomPattern = Pattern.compile(ATOM_PATTERN);
        Matcher atomMatcher = atomPattern.matcher(domain);
        while (match) {
            match = atomMatcher.find();
            if (match) {
                domainSegment[i] = atomMatcher.group(1);
                i++;
            }
        }

        int len = i;

        // Make sure there's a host name preceding the domain.
        if (len < 2) {
            return false;
        }

        // TODO: the tld should be checked against some sort of configurable
        // list
        String tld = domainSegment[len - 1];
        if (tld.length() > 1) {
            if (!Pattern.matches(TLD_PATTERN, tld)) {
                return false;
            }
        } else {
            return false;
        }

        return true;
    }

    /**
     * Recursively remove comments, and replace with a single space.  The simpler
     * regexps in the Email Addressing FAQ are imperfect - they will miss escaped
     * chars in atoms, for example.
     * Derived From    Mail::RFC822::Address
     *
     * @param emailStr The email address
     * @return address with comments removed.
     */
    protected String stripComments(String emailStr) {
        String input = emailStr;
        String result = emailStr;
        String commentPat = "s/^((?:[^\"\\\\]|\\\\.)*(?:\"(?:[^\"\\\\]|\\\\.)*\"(?:[^\"\\\\]|\111111\\\\.)*)*)\\((?:[^()\\\\]|\\\\.)*\\)/$1 /osx";
        Perl5Util commentMatcher = new Perl5Util();
        result = commentMatcher.substitute(commentPat, input);
        // This really needs to be =~ or Perl5Matcher comparison
        while (!result.equals(input)) {
            input = result;
            result = commentMatcher.substitute(commentPat, input);
        }
        return result;

    }
}
