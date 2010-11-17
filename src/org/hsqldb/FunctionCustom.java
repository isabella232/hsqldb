/* Copyright (c) 2001-2010, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.IntKeyIntValueHashMap;
import org.hsqldb.lib.StringConverter;
import org.hsqldb.lib.StringUtil;
import org.hsqldb.persist.Crypto;
import org.hsqldb.persist.HsqlDatabaseProperties;
import org.hsqldb.store.BitMap;
import org.hsqldb.store.ValuePool;
import org.hsqldb.types.ArrayType;
import org.hsqldb.types.BinaryData;
import org.hsqldb.types.BinaryType;
import org.hsqldb.types.BlobData;
import org.hsqldb.types.CharacterType;
import org.hsqldb.types.ClobData;
import org.hsqldb.types.DTIType;
import org.hsqldb.types.DateTimeType;
import org.hsqldb.types.IntervalMonthData;
import org.hsqldb.types.IntervalSecondData;
import org.hsqldb.types.IntervalType;
import org.hsqldb.types.LobData;
import org.hsqldb.types.NumberType;
import org.hsqldb.types.TimeData;
import org.hsqldb.types.TimestampData;
import org.hsqldb.types.Type;
import org.hsqldb.types.Types;

/**
 * Implementation of HSQLDB functions that are not defined by the
 * SQL standard.<p>
 *
 * Some functions are translated into equivalent SQL Standard functions.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.0.1
 * @since 1.9.0
 */
public class FunctionCustom extends FunctionSQL {

    public static final String[] openGroupNumericFunctions = {
        "ABS", "ACOS", "ASIN", "ATAN", "ATAN2", "BITAND", "BITOR", "BITXOR",
        "CEILING", "COS", "COT", "DEGREES", "EXP", "FLOOR", "LOG", "LOG10",
        "MOD", "PI", "POWER", "RADIANS", "RAND", "ROUND", "ROUNDMAGIC", "SIGN",
        "SIN", "SQRT", "TAN", "TRUNCATE"
    };
    public static final String[] openGroupStringFunctions = {
        "ASCII", "CHAR", "CONCAT", "DIFFERENCE", "HEXTORAW", "INSERT", "LCASE",
        "LEFT", "LENGTH", "LOCATE", "LTRIM", "RAWTOHEX", "REPEAT", "REPLACE",
        "RIGHT", "RTRIM", "SOUNDEX", "SPACE", "SUBSTR", "UCASE",
    };
    public static final String[] openGroupDateTimeFunctions = {
        "CURDATE", "CURTIME", "DATEDIFF", "DAYNAME", "DAYOFMONTH", "DAYOFWEEK",
        "DAYOFYEAR", "HOUR", "MINUTE", "MONTH", "MONTHNAME", "NOW", "QUARTER",
        "SECOND", "SECONDS_SINCE_MIDNIGHT", "TIMESTAMPADD", "TIMESTAMPDIFF",
        "TO_CHAR", "WEEK", "YEAR"
    };
    public static final String[] openGroupSystemFunctions = {
        "DATABASE", "IFNULL", "USER"
    };

    //
    private final static int FUNC_ACOS                     = 71;
    private final static int FUNC_ACTION_ID                = 72;
    private final static int FUNC_ASCII                    = 73;
    private final static int FUNC_ASIN                     = 74;
    private final static int FUNC_ATAN                     = 75;
    private final static int FUNC_ATAN2                    = 76;
    private final static int FUNC_BITAND                   = 77;
    private final static int FUNC_BITOR                    = 78;
    private final static int FUNC_BITXOR                   = 79;
    private final static int FUNC_CHAR                     = 80;
    private final static int FUNC_CONCAT                   = 81;
    private final static int FUNC_COS                      = 82;
    private final static int FUNC_COT                      = 83;
    private final static int FUNC_CRYPT_KEY                = 84;
    private final static int FUNC_DATABASE                 = 85;
    private final static int FUNC_DATABASE_ISOLATION_LEVEL = 86;
    private final static int FUNC_DATABASE_NAME            = 87;
    private final static int FUNC_DATABASE_TIMEZONE        = 88;
    private final static int FUNC_DATABASE_VERSION         = 89;
    private final static int FUNC_DATEADD                  = 90;
    private final static int FUNC_DATEDIFF                 = 91;
    private final static int FUNC_DEGREES                  = 92;
    private final static int FUNC_DIFFERENCE               = 93;
    private final static int FUNC_HEXTORAW                 = 94;
    private final static int FUNC_IDENTITY                 = 95;
    private final static int FUNC_ISAUTOCOMMIT             = 96;
    private final static int FUNC_ISOLATION_LEVEL          = 97;
    private final static int FUNC_ISREADONLYDATABASE       = 98;
    private final static int FUNC_ISREADONLYDATABASEFILES  = 99;
    private final static int FUNC_ISREADONLYSESSION        = 100;
    private final static int FUNC_LEFT                     = 101;
    private final static int FUNC_LOAD_FILE                = 102;
    private final static int FUNC_LOB_ID                   = 103;
    private final static int FUNC_LOCATE                   = 104;
    private final static int FUNC_LOG10                    = 105;
    private final static int FUNC_LPAD                     = 106;
    private final static int FUNC_LTRIM                    = 107;
    private final static int FUNC_PI                       = 108;
    private final static int FUNC_RADIANS                  = 109;
    private final static int FUNC_RAND                     = 110;
    private final static int FUNC_RAWTOHEX                 = 111;
    private final static int FUNC_REGEXP_MATCHES           = 112;
    private final static int FUNC_REPEAT                   = 113;
    private final static int FUNC_REPLACE                  = 114;
    private final static int FUNC_REVERSE                  = 115;
    private final static int FUNC_RIGHT                    = 116;
    private final static int FUNC_ROUND                    = 117;
    private final static int FUNC_ROUNDMAGIC               = 118;
    private final static int FUNC_RPAD                     = 119;
    private final static int FUNC_RTRIM                    = 120;
    private final static int FUNC_SECONDS_MIDNIGHT         = 121;
    private final static int FUNC_SEQUENCE_ARRAY           = 122;
    private final static int FUNC_SESSION_ID               = 123;
    private final static int FUNC_SESSION_ISOLATION_LEVEL  = 124;
    private final static int FUNC_SESSION_TIMEZONE         = 125;
    private final static int FUNC_SIGN                     = 126;
    private final static int FUNC_SIN                      = 127;
    private final static int FUNC_SOUNDEX                  = 128;
    private final static int FUNC_SPACE                    = 129;
    private final static int FUNC_SUBSTR                   = 130;
    private final static int FUNC_SYSDATE                  = 131;
    private final static int FUNC_TAN                      = 132;
    private final static int FUNC_TIMESTAMP                = 133;
    private final static int FUNC_TIMESTAMPADD             = 134;
    private final static int FUNC_TIMESTAMPDIFF            = 135;
    private final static int FUNC_TIMEZONE                 = 136;
    private final static int FUNC_TO_CHAR                  = 137;
    private final static int FUNC_TO_DATE                  = 138;
    private final static int FUNC_TO_TIMESTAMP             = 139;
    private final static int FUNC_TRANSACTION_CONTROL      = 140;
    private final static int FUNC_TRANSACTION_ID           = 141;
    private final static int FUNC_TRANSACTION_SIZE         = 142;
    private final static int FUNC_TRUNC                    = 143;
    private final static int FUNC_TRUNCATE                 = 144;
    private final static int FUNC_UUID                     = 145;
    private final static int FUNC_UNIX_TIMESTAMP           = 146;

    //
    static final IntKeyIntValueHashMap customRegularFuncMap =
        new IntKeyIntValueHashMap();

    static {
        //J-

        nonDeterministicFuncSet.add(FUNC_ACTION_ID);
        nonDeterministicFuncSet.add(FUNC_CRYPT_KEY);
        nonDeterministicFuncSet.add(FUNC_DATABASE);
        nonDeterministicFuncSet.add(FUNC_DATABASE_ISOLATION_LEVEL);
        nonDeterministicFuncSet.add(FUNC_DATABASE_TIMEZONE);
        nonDeterministicFuncSet.add(FUNC_IDENTITY);
        nonDeterministicFuncSet.add(FUNC_ISAUTOCOMMIT);
        nonDeterministicFuncSet.add(FUNC_ISREADONLYSESSION);
        nonDeterministicFuncSet.add(FUNC_ISREADONLYDATABASE);
        nonDeterministicFuncSet.add(FUNC_ISREADONLYDATABASEFILES);
        nonDeterministicFuncSet.add(FUNC_ISOLATION_LEVEL);
        nonDeterministicFuncSet.add(FUNC_SESSION_ID);
        nonDeterministicFuncSet.add(FUNC_SESSION_ISOLATION_LEVEL);
        nonDeterministicFuncSet.add(FUNC_SESSION_TIMEZONE);
        nonDeterministicFuncSet.add(FUNC_TIMESTAMP);
        nonDeterministicFuncSet.add(FUNC_TIMEZONE);
        nonDeterministicFuncSet.add(FUNC_TRANSACTION_CONTROL);
        nonDeterministicFuncSet.add(FUNC_TRANSACTION_ID);
        nonDeterministicFuncSet.add(FUNC_TRANSACTION_SIZE);
        nonDeterministicFuncSet.add(FUNC_UUID);
        nonDeterministicFuncSet.add(FUNC_UNIX_TIMESTAMP);

        //
        customRegularFuncMap.put(Tokens.ACOS, FUNC_ACOS);
        customRegularFuncMap.put(Tokens.ACTION_ID, FUNC_ACTION_ID);
        customRegularFuncMap.put(Tokens.ASCII, FUNC_ASCII);
        customRegularFuncMap.put(Tokens.ASIN, FUNC_ASIN);
        customRegularFuncMap.put(Tokens.ATAN, FUNC_ATAN);
        customRegularFuncMap.put(Tokens.ATAN2, FUNC_ATAN2);
        customRegularFuncMap.put(Tokens.BITAND, FUNC_BITAND);
        customRegularFuncMap.put(Tokens.BITLENGTH, FUNC_BIT_LENGTH);
        customRegularFuncMap.put(Tokens.BITOR, FUNC_BITOR);
        customRegularFuncMap.put(Tokens.BITXOR, FUNC_BITXOR);
        customRegularFuncMap.put(Tokens.CHAR, FUNC_CHAR);
        customRegularFuncMap.put(Tokens.CHR, FUNC_CHAR);
        customRegularFuncMap.put(Tokens.CONCAT_WORD, FUNC_CONCAT);
        customRegularFuncMap.put(Tokens.COS, FUNC_COS);
        customRegularFuncMap.put(Tokens.COT, FUNC_COT);
        customRegularFuncMap.put(Tokens.CRYPT_KEY, FUNC_CRYPT_KEY);
        customRegularFuncMap.put(Tokens.CURDATE, FUNC_CURRENT_DATE);
        customRegularFuncMap.put(Tokens.CURTIME, FUNC_LOCALTIME);
        customRegularFuncMap.put(Tokens.DATABASE, FUNC_DATABASE);
        customRegularFuncMap.put(Tokens.DATABASE_NAME, FUNC_DATABASE_NAME);
        customRegularFuncMap.put(Tokens.DATABASE_ISOLATION_LEVEL, FUNC_DATABASE_ISOLATION_LEVEL);
        customRegularFuncMap.put(Tokens.DATABASE_TIMEZONE, FUNC_DATABASE_TIMEZONE);
        customRegularFuncMap.put(Tokens.DATABASE_VERSION, FUNC_DATABASE_VERSION);
        customRegularFuncMap.put(Tokens.DATEADD, FUNC_DATEADD);
        customRegularFuncMap.put(Tokens.DATEDIFF, FUNC_DATEDIFF);
        customRegularFuncMap.put(Tokens.DAY, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.DAYNAME, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.DAYOFMONTH, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.DAYOFWEEK, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.DAYOFYEAR, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.DEGREES, FUNC_DEGREES);
        customRegularFuncMap.put(Tokens.DIFFERENCE, FUNC_DIFFERENCE);
        customRegularFuncMap.put(Tokens.HEXTORAW, FUNC_HEXTORAW);
        customRegularFuncMap.put(Tokens.HOUR, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.IDENTITY, FUNC_IDENTITY);
        customRegularFuncMap.put(Tokens.INSERT, FUNC_OVERLAY_CHAR);
        customRegularFuncMap.put(Tokens.IS_AUTOCOMMIT, FUNC_ISAUTOCOMMIT);
        customRegularFuncMap.put(Tokens.IS_READONLY_DATABASE, FUNC_ISREADONLYDATABASE);
        customRegularFuncMap.put(Tokens.IS_READONLY_DATABASE_FILES, FUNC_ISREADONLYDATABASEFILES);
        customRegularFuncMap.put(Tokens.IS_READONLY_SESSION, FUNC_ISREADONLYSESSION);
        customRegularFuncMap.put(Tokens.ISOLATION_LEVEL, FUNC_ISOLATION_LEVEL);
        customRegularFuncMap.put(Tokens.LCASE, FUNC_FOLD_LOWER);
        customRegularFuncMap.put(Tokens.LEFT, FUNC_LEFT);
        customRegularFuncMap.put(Tokens.LENGTH, FUNC_CHAR_LENGTH);
        customRegularFuncMap.put(Tokens.LOAD_FILE, FUNC_LOAD_FILE);
        customRegularFuncMap.put(Tokens.LOB_ID, FUNC_LOB_ID);
        customRegularFuncMap.put(Tokens.LOCATE, FUNC_POSITION_CHAR);
        customRegularFuncMap.put(Tokens.LOG, FUNC_LN);
        customRegularFuncMap.put(Tokens.LOG10, FUNC_LOG10);
        customRegularFuncMap.put(Tokens.LPAD, FUNC_LPAD);
        customRegularFuncMap.put(Tokens.LTRIM, FUNC_TRIM_CHAR);
        customRegularFuncMap.put(Tokens.MINUTE, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.MONTH, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.MONTHNAME, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.OCTETLENGTH, FUNC_OCTET_LENGTH);
        customRegularFuncMap.put(Tokens.PI, FUNC_PI);
        customRegularFuncMap.put(Tokens.QUARTER, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.RADIANS, FUNC_RADIANS);
        customRegularFuncMap.put(Tokens.RAND, FUNC_RAND);
        customRegularFuncMap.put(Tokens.RAWTOHEX, FUNC_RAWTOHEX);
        customRegularFuncMap.put(Tokens.REGEXP_MATCHES, FUNC_REGEXP_MATCHES);
        customRegularFuncMap.put(Tokens.REPEAT, FUNC_REPEAT);
        customRegularFuncMap.put(Tokens.REPLACE, FUNC_REPLACE);
        customRegularFuncMap.put(Tokens.REVERSE, FUNC_REVERSE);
        customRegularFuncMap.put(Tokens.RIGHT, FUNC_RIGHT);
        customRegularFuncMap.put(Tokens.ROUND, FUNC_ROUND);
        customRegularFuncMap.put(Tokens.ROUNDMAGIC, FUNC_ROUNDMAGIC);
        customRegularFuncMap.put(Tokens.RPAD, FUNC_RPAD);
        customRegularFuncMap.put(Tokens.RTRIM, FUNC_TRIM_CHAR);
        customRegularFuncMap.put(Tokens.SECOND, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.SECONDS_MIDNIGHT, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.SEQUENCE_ARRAY, FUNC_SEQUENCE_ARRAY);
        customRegularFuncMap.put(Tokens.SESSION_ID, FUNC_SESSION_ID);
        customRegularFuncMap.put(Tokens.SESSION_ISOLATION_LEVEL, FUNC_SESSION_ISOLATION_LEVEL);
        customRegularFuncMap.put(Tokens.SESSION_TIMEZONE, FUNC_SESSION_TIMEZONE);
        customRegularFuncMap.put(Tokens.SIGN, FUNC_SIGN);
        customRegularFuncMap.put(Tokens.SIN, FUNC_SIN);
        customRegularFuncMap.put(Tokens.SOUNDEX, FUNC_SOUNDEX);
        customRegularFuncMap.put(Tokens.SPACE, FUNC_SPACE);
        customRegularFuncMap.put(Tokens.SUBSTR, FUNC_SUBSTRING_CHAR);
        customRegularFuncMap.put(Tokens.TAN, FUNC_TAN);
        customRegularFuncMap.put(Tokens.TIMESTAMP, FUNC_TIMESTAMP);
        customRegularFuncMap.put(Tokens.TIMESTAMPADD, FUNC_TIMESTAMPADD);
        customRegularFuncMap.put(Tokens.TIMESTAMPDIFF, FUNC_TIMESTAMPDIFF);
        customRegularFuncMap.put(Tokens.TIMEZONE, FUNC_TIMEZONE);
        customRegularFuncMap.put(Tokens.TO_CHAR, FUNC_TO_CHAR);
        customRegularFuncMap.put(Tokens.TO_DATE, FUNC_TO_DATE);
        customRegularFuncMap.put(Tokens.TO_TIMESTAMP, FUNC_TO_TIMESTAMP);
        customRegularFuncMap.put(Tokens.TRANSACTION_CONTROL, FUNC_TRANSACTION_CONTROL);
        customRegularFuncMap.put(Tokens.TRANSACTION_ID, FUNC_TRANSACTION_ID);
        customRegularFuncMap.put(Tokens.TRANSACTION_SIZE, FUNC_TRANSACTION_SIZE);
        customRegularFuncMap.put(Tokens.TRUNC, FUNC_TRUNC);
        customRegularFuncMap.put(Tokens.TRUNCATE, FUNC_TRUNCATE);
        customRegularFuncMap.put(Tokens.UCASE, FUNC_FOLD_UPPER);
        customRegularFuncMap.put(Tokens.UNIX_TIMESTAMP, FUNC_UNIX_TIMESTAMP);
        customRegularFuncMap.put(Tokens.UUID, FUNC_UUID);
        customRegularFuncMap.put(Tokens.WEEK, FUNC_EXTRACT);
        customRegularFuncMap.put(Tokens.YEAR, FUNC_EXTRACT);
        //J+
    }

    static final IntKeyIntValueHashMap customValueFuncMap =
        new IntKeyIntValueHashMap();

    static {
        customValueFuncMap.put(Tokens.SYSDATE, FUNC_LOCALTIMESTAMP);
        customValueFuncMap.put(Tokens.TODAY, FUNC_CURRENT_DATE);
        customValueFuncMap.put(Tokens.NOW, FUNC_LOCALTIMESTAMP);
    }

    private int     extractSpec;
    private String  matchPattern;
    private Pattern pattern;

    public static FunctionSQL newCustomFunction(String token, int tokenType) {

        int id = customRegularFuncMap.get(tokenType, -1);

        if (id == -1) {
            id = customValueFuncMap.get(tokenType, -1);
        }

        if (id == -1) {
            return null;
        }

        switch (tokenType) {

            case Tokens.BITLENGTH :
            case Tokens.LCASE :
            case Tokens.LENGTH :
            case Tokens.LOG :
            case Tokens.OCTETLENGTH :
            case Tokens.TODAY :
            case Tokens.SYSDATE :
            case Tokens.UCASE :
                return new FunctionSQL(id);

            case Tokens.NOW : {
                FunctionSQL function = new FunctionSQL(id);

                function.parseList = optionalNoParamList;

                return function;
            }
            case Tokens.CURDATE :
            case Tokens.CURTIME : {
                FunctionSQL function = new FunctionSQL(id);

                function.parseList = emptyParamList;

                return function;
            }
            case Tokens.SUBSTR : {
                FunctionSQL function = new FunctionSQL(id);

                function.parseList = tripleParamList;

                return function;
            }
        }

        FunctionCustom function = new FunctionCustom(id);

        if (id == FUNC_TRIM_CHAR) {
            switch (tokenType) {

                case Tokens.LTRIM :
                    function.extractSpec = Tokens.LEADING;
                    break;

                case Tokens.RTRIM :
                    function.extractSpec = Tokens.TRAILING;
                    break;
            }
        }

        if (id == FUNC_EXTRACT) {
            switch (tokenType) {

                case Tokens.DAYNAME :
                    function.extractSpec = Tokens.DAY_NAME;
                    break;

                case Tokens.MONTHNAME :
                    function.extractSpec = Tokens.MONTH_NAME;
                    break;

                case Tokens.DAYOFMONTH :
                    function.extractSpec = Tokens.DAY_OF_MONTH;
                    break;

                case Tokens.DAYOFWEEK :
                    function.extractSpec = Tokens.DAY_OF_WEEK;
                    break;

                case Tokens.DAYOFYEAR :
                    function.extractSpec = Tokens.DAY_OF_YEAR;
                    break;

                case Tokens.WEEK :
                    function.extractSpec = Tokens.WEEK_OF_YEAR;
                    break;

                default :
                    function.extractSpec = tokenType;
            }
        }

        if (function.name == null) {
            function.name = token;
        }

        return function;
    }

    public static boolean isRegularFunction(int tokenType) {
        return customRegularFuncMap.get(tokenType, -1) != -1;
    }

    public static boolean isValueFunction(int tokenType) {
        return customValueFuncMap.get(tokenType, -1) != -1;
    }

    private FunctionCustom(int id) {

        super();

        this.funcType   = id;
        isDeterministic = !nonDeterministicFuncSet.contains(id);

        switch (id) {

            case FUNC_ACTION_ID :
            case FUNC_DATABASE :
            case FUNC_DATABASE_ISOLATION_LEVEL :
            case FUNC_DATABASE_NAME :
            case FUNC_DATABASE_TIMEZONE :
            case FUNC_DATABASE_VERSION :
            case FUNC_ISAUTOCOMMIT :
            case FUNC_ISOLATION_LEVEL :
            case FUNC_ISREADONLYDATABASE :
            case FUNC_ISREADONLYDATABASEFILES :
            case FUNC_ISREADONLYSESSION :
            case FUNC_PI :
            case FUNC_SESSION_ID :
            case FUNC_SESSION_ISOLATION_LEVEL :
            case FUNC_SESSION_TIMEZONE :
            case FUNC_TIMEZONE :
            case FUNC_TRANSACTION_CONTROL :
            case FUNC_TRANSACTION_ID :
            case FUNC_TRANSACTION_SIZE :
                parseList = emptyParamList;
                break;

            case FUNC_ACOS :
            case FUNC_ASCII :
            case FUNC_ASIN :
            case FUNC_ATAN :
            case FUNC_CHAR :
            case FUNC_COS :
            case FUNC_COT :
            case FUNC_DEGREES :
            case FUNC_HEXTORAW :
            case FUNC_LOB_ID :
            case FUNC_LOG10 :
            case FUNC_RADIANS :
            case FUNC_RAWTOHEX :
            case FUNC_REVERSE :
            case FUNC_ROUNDMAGIC :
            case FUNC_SIGN :
            case FUNC_SIN :
            case FUNC_SOUNDEX :
            case FUNC_SPACE :
            case FUNC_TAN :
                parseList = singleParamList;
                break;

            case FUNC_TO_CHAR :
            case FUNC_TO_DATE :
            case FUNC_TO_TIMESTAMP :
            case FUNC_CONCAT :
            case FUNC_LEFT :
            case FUNC_ATAN2 :
            case FUNC_BITAND :
            case FUNC_BITOR :
            case FUNC_BITXOR :
            case FUNC_DIFFERENCE :
            case FUNC_REGEXP_MATCHES :
            case FUNC_REPEAT :
            case FUNC_CRYPT_KEY :
            case FUNC_RIGHT :
                parseList = doubleParamList;
                break;

            case FUNC_DATEADD :
            case FUNC_DATEDIFF :
            case FUNC_REPLACE :
            case FUNC_SEQUENCE_ARRAY :
                parseList = tripleParamList;
                break;

            case FUNC_LPAD :
            case FUNC_RPAD :
            case FUNC_POSITION_CHAR :
                parseList = new short[] {
                    Tokens.OPENBRACKET, Tokens.QUESTION, Tokens.COMMA,
                    Tokens.QUESTION, Tokens.X_OPTION, 2, Tokens.COMMA,
                    Tokens.QUESTION, Tokens.CLOSEBRACKET
                };
                break;

            case FUNC_UNIX_TIMESTAMP :
            case FUNC_UUID :
                parseList = optionalSingleParamList;
                break;

            case FUNC_EXTRACT :
                name      = Tokens.T_EXTRACT;
                parseList = singleParamList;
                break;

            case FUNC_TRIM_CHAR :
                name      = Tokens.T_TRIM;
                parseList = singleParamList;
                break;

            case FUNC_OVERLAY_CHAR :
                name      = Tokens.T_OVERLAY;
                parseList = quadParamList;
                break;

            case FUNC_IDENTITY :
                name      = Tokens.T_IDENTITY;
                parseList = emptyParamList;
                break;

            case FUNC_TIMESTAMPADD :
                name      = Tokens.T_TIMESTAMPADD;
                parseList = new short[] {
                    Tokens.OPENBRACKET, Tokens.X_KEYSET, 9,
                    Tokens.SQL_TSI_FRAC_SECOND, Tokens.SQL_TSI_SECOND,
                    Tokens.SQL_TSI_MINUTE, Tokens.SQL_TSI_HOUR,
                    Tokens.SQL_TSI_DAY, Tokens.SQL_TSI_WEEK,
                    Tokens.SQL_TSI_MONTH, Tokens.SQL_TSI_QUARTER,
                    Tokens.SQL_TSI_YEAR, Tokens.COMMA, Tokens.QUESTION,
                    Tokens.COMMA, Tokens.QUESTION, Tokens.CLOSEBRACKET
                };
                break;

            case FUNC_TIMESTAMPDIFF :
                name      = Tokens.T_TIMESTAMPDIFF;
                parseList = new short[] {
                    Tokens.OPENBRACKET, Tokens.X_KEYSET, 9,
                    Tokens.SQL_TSI_FRAC_SECOND, Tokens.SQL_TSI_SECOND,
                    Tokens.SQL_TSI_MINUTE, Tokens.SQL_TSI_HOUR,
                    Tokens.SQL_TSI_DAY, Tokens.SQL_TSI_WEEK,
                    Tokens.SQL_TSI_MONTH, Tokens.SQL_TSI_QUARTER,
                    Tokens.SQL_TSI_YEAR, Tokens.COMMA, Tokens.QUESTION,
                    Tokens.COMMA, Tokens.QUESTION, Tokens.CLOSEBRACKET
                };
                break;

            case FUNC_LOAD_FILE :
            case FUNC_ROUND :
            case FUNC_TIMESTAMP :
            case FUNC_TRUNC :
            case FUNC_TRUNCATE :
                parseList = new short[] {
                    Tokens.OPENBRACKET, Tokens.QUESTION, Tokens.X_OPTION, 2,
                    Tokens.COMMA, Tokens.QUESTION, Tokens.CLOSEBRACKET
                };
                break;

            case FUNC_RAND :
                parseList = optionalSingleParamList;
                break;

            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "FunctionCustom");
        }
    }

    public void setArguments(Expression[] nodes) {

        switch (funcType) {

            case FUNC_POSITION_CHAR : {

                // LOCATE
                Expression[] newNodes = new Expression[4];

                newNodes[0] = nodes[0];
                newNodes[1] = nodes[1];
                newNodes[3] = nodes[2];
                nodes       = newNodes;

                break;
            }
            case FUNC_OVERLAY_CHAR : {
                Expression start  = nodes[1];
                Expression length = nodes[2];

                nodes[1] = nodes[3];
                nodes[2] = start;
                nodes[3] = length;

                break;
            }
            case FUNC_EXTRACT : {
                Expression[] newNodes = new Expression[2];

                newNodes[0] =
                    new ExpressionValue(ValuePool.getInt(extractSpec),
                                        Type.SQL_INTEGER);
                newNodes[1] = nodes[0];
                nodes       = newNodes;

                break;
            }
            case FUNC_TRIM_CHAR : {
                Expression[] newNodes = new Expression[3];

                newNodes[0] =
                    new ExpressionValue(ValuePool.getInt(extractSpec),
                                        Type.SQL_INTEGER);
                newNodes[1] = new ExpressionValue(" ", Type.SQL_CHAR);
                newNodes[2] = nodes[0];
                nodes       = newNodes;
            }
        }

        super.setArguments(nodes);
    }

    public Expression getFunctionExpression() {

        switch (funcType) {

            case FUNC_CONCAT :
                return new ExpressionArithmetic(OpTypes.CONCAT,
                                                nodes[Expression.LEFT],
                                                nodes[Expression.RIGHT]);
        }

        return super.getFunctionExpression();
    }

    Object getValue(Session session, Object[] data) {

        switch (funcType) {

            case FUNC_POSITION_CHAR :
            case FUNC_EXTRACT :
            case FUNC_TRIM_CHAR :
            case FUNC_OVERLAY_CHAR :
                return super.getValue(session, data);

            case FUNC_DATABASE :
                return session.getDatabase().getPath();

            case FUNC_DATABASE_NAME :
                return session.getDatabase().getUniqueName();

            case FUNC_ISAUTOCOMMIT :
                return session.isAutoCommit() ? Boolean.TRUE
                                              : Boolean.FALSE;

            case FUNC_ISREADONLYSESSION :
                return session.isReadOnlyDefault() ? Boolean.TRUE
                                                   : Boolean.FALSE;

            case FUNC_ISREADONLYDATABASE :
                return session.getDatabase().databaseReadOnly ? Boolean.TRUE
                                                              : Boolean.FALSE;

            case FUNC_ISREADONLYDATABASEFILES :
                return session.getDatabase().isFilesReadOnly() ? Boolean.TRUE
                                                               : Boolean.FALSE;

            case FUNC_ISOLATION_LEVEL : {
                return Session.getIsolationString(session.isolationLevel);
            }
            case FUNC_SESSION_ISOLATION_LEVEL :
                return Session.getIsolationString(
                    session.isolationLevelDefault);

            case FUNC_DATABASE_ISOLATION_LEVEL :
                return Session.getIsolationString(
                    session.database.defaultIsolationLevel);

            case FUNC_TRANSACTION_CONTROL :
                switch (session.database.txManager.getTransactionControl()) {

                    case TransactionManager.MVCC :
                        return Tokens.T_MVCC;

                    case TransactionManager.MVLOCKS :
                        return Tokens.T_MVLOCKS;

                    case TransactionManager.LOCKS :
                    default :
                        return Tokens.T_LOCKS;
                }
            case FUNC_TIMEZONE :
                return new IntervalSecondData(session.getZoneSeconds(), 0);

            case FUNC_SESSION_TIMEZONE :
                return new IntervalSecondData(session.sessionTimeZoneSeconds,
                                              0);

            case FUNC_DATABASE_TIMEZONE :
                int sec =
                    HsqlDateTime.getZoneSeconds(HsqlDateTime.tempCalDefault);

                return new IntervalSecondData(sec, 0);

            case FUNC_DATABASE_VERSION :
                return HsqlDatabaseProperties.THIS_FULL_VERSION;

            case FUNC_SESSION_ID : {
                return Long.valueOf(session.getId());
            }
            case FUNC_ACTION_ID : {
                return Long.valueOf(session.actionTimestamp);
            }
            case FUNC_TRANSACTION_ID : {
                return Long.valueOf(session.transactionTimestamp);
            }
            case FUNC_TRANSACTION_SIZE : {
                return Long.valueOf(session.actionIndex);
            }
            case FUNC_LOB_ID : {
                LobData lob = (LobData) data[0];

                if (lob == null) {
                    return null;
                }

                return Long.valueOf(lob.getId());
            }
            case FUNC_IDENTITY : {
                Number id = session.getLastIdentity();

                if (id instanceof Long) {
                    return id;
                } else {
                    return ValuePool.getLong(id.longValue());
                }
            }
            case FUNC_SEQUENCE_ARRAY : {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == null) {
                        return null;
                    }
                }

                HsqlArrayList list    = new HsqlArrayList();
                Object        current = data[0];
                Type          type    = nodes[0].getDataType();
                boolean ascending = type.compare(session, data[1], data[0])
                                    >= 0;

                while (true) {
                    int compare = type.compare(session, current, data[1]);

                    if (ascending) {
                        if (compare > 0) {
                            break;
                        }
                    } else if (compare < 0) {
                        break;
                    }

                    list.add(current);

                    Object newValue = type.add(current, data[2],
                                               nodes[2].getDataType());

                    compare = type.compare(session, current, newValue);

                    if (ascending) {
                        if (compare >= 0) {
                            break;
                        }
                    } else if (compare <= 0) {
                        break;
                    }

                    current = newValue;
                }

                Object[] array = list.toArray();

                return array;
            }
            case FUNC_TIMESTAMPADD : {
                if (data[1] == null || data[2] == null) {
                    return null;
                }

                data[1] = Type.SQL_BIGINT.convertToType(session, data[1],
                        nodes[1].getDataType());

                int           part = ((Number) nodes[0].valueData).intValue();
                long          units  = ((Number) data[1]).longValue();
                TimestampData source = (TimestampData) data[2];
                IntervalType  t;
                Object        o;

                switch (part) {

                    case Tokens.SQL_TSI_FRAC_SECOND : {
                        long seconds = units / DTIType.limitNanoseconds;
                        int  nanos = (int) (units % DTIType.limitNanoseconds);

                        t = Type.SQL_INTERVAL_SECOND_MAX_FRACTION;
                        o = new IntervalSecondData(seconds, nanos, t);

                        return dataType.add(source, o, t);
                    }
                    case Tokens.SQL_TSI_SECOND :
                        t = Type.SQL_INTERVAL_SECOND_MAX_PRECISION;
                        o = IntervalSecondData.newIntervalSeconds(units, t);

                        return dataType.add(source, o, t);

                    case Tokens.SQL_TSI_MINUTE :
                        t = Type.SQL_INTERVAL_MINUTE_MAX_PRECISION;
                        o = IntervalSecondData.newIntervalMinute(units, t);

                        return dataType.add(source, o, t);

                    case Tokens.SQL_TSI_HOUR :
                        t = Type.SQL_INTERVAL_HOUR_MAX_PRECISION;
                        o = IntervalSecondData.newIntervalHour(units, t);

                        return dataType.add(source, o, t);

                    case Tokens.SQL_TSI_DAY :
                        t = Type.SQL_INTERVAL_DAY_MAX_PRECISION;
                        o = IntervalSecondData.newIntervalDay(units, t);

                        return dataType.add(source, o, t);

                    case Tokens.SQL_TSI_WEEK :
                        t = Type.SQL_INTERVAL_DAY_MAX_PRECISION;
                        o = IntervalSecondData.newIntervalDay(units * 7, t);

                        return dataType.add(source, o, t);

                    case Tokens.SQL_TSI_MONTH :
                        t = Type.SQL_INTERVAL_MONTH_MAX_PRECISION;
                        o = IntervalMonthData.newIntervalMonth(units, t);

                        return dataType.add(source, o, t);

                    case Tokens.SQL_TSI_QUARTER :
                        t = Type.SQL_INTERVAL_MONTH_MAX_PRECISION;
                        o = IntervalMonthData.newIntervalMonth(units * 3, t);

                        return dataType.add(source, o, t);

                    case Tokens.SQL_TSI_YEAR :
                        t = Type.SQL_INTERVAL_YEAR_MAX_PRECISION;
                        o = IntervalMonthData.newIntervalMonth(units * 12, t);

                        return dataType.add(source, o, t);

                    default :
                        throw Error.runtimeError(ErrorCode.U_S0500,
                                                 "FunctionCustom");
                }
            }
            case FUNC_TIMESTAMPDIFF : {
                if (data[1] == null || data[2] == null) {
                    return null;
                }

                int           part = ((Number) nodes[0].valueData).intValue();
                TimestampData a    = (TimestampData) data[2];
                TimestampData b    = (TimestampData) data[1];

                if (nodes[2].dataType.isDateTimeTypeWithZone()) {
                    a = (TimestampData) Type.SQL_TIMESTAMP.convertToType(
                        session, a, Type.SQL_TIMESTAMP_WITH_TIME_ZONE);
                }

                if (nodes[1].dataType.isDateTimeTypeWithZone()) {
                    b = (TimestampData) Type.SQL_TIMESTAMP.convertToType(
                        session, b, Type.SQL_TIMESTAMP_WITH_TIME_ZONE);
                }

                IntervalType t;

                switch (part) {

                    case Tokens.SQL_TSI_FRAC_SECOND :
                        t = Type.SQL_INTERVAL_SECOND_MAX_PRECISION;

                        IntervalSecondData interval =
                            (IntervalSecondData) t.subtract(a, b, null);

                        return new Long(
                            DTIType.limitNanoseconds * interval.getSeconds()
                            + interval.getNanos());

                    case Tokens.SQL_TSI_SECOND :
                        t = Type.SQL_INTERVAL_SECOND_MAX_PRECISION;

                        return new Long(t.convertToLong(t.subtract(a, b,
                                null)));

                    case Tokens.SQL_TSI_MINUTE :
                        t = Type.SQL_INTERVAL_MINUTE_MAX_PRECISION;

                        return new Long(t.convertToLong(t.subtract(a, b,
                                null)));

                    case Tokens.SQL_TSI_HOUR :
                        t = Type.SQL_INTERVAL_HOUR_MAX_PRECISION;

                        return new Long(t.convertToLong(t.subtract(a, b,
                                null)));

                    case Tokens.SQL_TSI_DAY :
                        t = Type.SQL_INTERVAL_DAY_MAX_PRECISION;

                        return new Long(t.convertToLong(t.subtract(a, b,
                                null)));

                    case Tokens.SQL_TSI_WEEK :
                        t = Type.SQL_INTERVAL_DAY_MAX_PRECISION;

                        return new Long(t.convertToLong(t.subtract(a, b, null))
                                        / 7);

                    case Tokens.SQL_TSI_MONTH :
                        t = Type.SQL_INTERVAL_MONTH_MAX_PRECISION;

                        return new Long(t.convertToLong(t.subtract(a, b,
                                null)));

                    case Tokens.SQL_TSI_QUARTER :
                        t = Type.SQL_INTERVAL_MONTH_MAX_PRECISION;

                        return new Long(t.convertToLong(t.subtract(a, b, null))
                                        / 3);

                    case Tokens.SQL_TSI_YEAR :
                        t = Type.SQL_INTERVAL_YEAR_MAX_PRECISION;

                        return new Long(t.convertToLong(t.subtract(a, b,
                                null)));

                    default :
                        throw Error.runtimeError(ErrorCode.U_S0500,
                                                 "FunctionCustom");
                }
            }
            case FUNC_ROUND :
            case FUNC_TRUNC : {
                int interval = Types.SQL_INTERVAL_DAY;

                if (data[0] == null) {
                    return null;
                }

                if (dataType.isDateTimeType()) {
                    DateTimeType type = (DateTimeType) dataType;

                    if (nodes.length > 1 && nodes[1] != null) {
                        if (data[1] == null) {
                            return null;
                        }

                        interval = HsqlDateTime.toStandardIntervalPart(
                            (String) data[1]);
                    }

                    if (interval < 0) {
                        throw Error.error(ErrorCode.X_42566, (String) data[1]);
                    }

                    return funcType == FUNC_ROUND
                           ? type.round(data[0], interval)
                           : type.truncate(data[0], interval);
                }
            }

            // fall through
            case FUNC_TRUNCATE : {
                int offset = 0;

                if (data[0] == null) {
                    return null;
                }

                if (nodes.length > 1) {
                    if (data[1] == null) {
                        return null;
                    }

                    data[1] = Type.SQL_INTEGER.convertToType(session, data[1],
                            nodes[1].getDataType());
                    offset = ((Number) data[1]).intValue();
                }

                return funcType == FUNC_ROUND
                       ? ((NumberType) dataType).round(data[0], offset)
                       : ((NumberType) dataType).truncate(data[0], offset);
            }
            case FUNC_TO_CHAR : {
                if (data[0] == null || data[1] == null) {
                    return null;
                }

                SimpleDateFormat format = session.getSimpleDateFormatGMT();
                Date date =
                    (Date) ((DateTimeType) nodes[0].dataType)
                        .convertSQLToJavaGMT(session, data[0]);

                return HsqlDateTime.toFormattedDate(date, (String) data[1],
                                                    format);
            }
            case FUNC_TO_DATE :
            case FUNC_TO_TIMESTAMP : {
                if (data[0] == null || data[1] == null) {
                    return null;
                }

                SimpleDateFormat format = session.getSimpleDateFormatGMT();
                Date date = HsqlDateTime.toDate((String) data[0],
                                                (String) data[1], format);
                long millis = date.getTime();
                int  nanos  = 0;

                if (funcType == FUNC_TO_DATE) {
                    millis = HsqlDateTime.convertToNormalisedDate(millis,
                            format.getCalendar());
                } else {
                    nanos = ((int) (millis % 1000)) * 100000;
                }

                return new TimestampData(millis / 1000, nanos);
            }
            case FUNC_TIMESTAMP : {
                boolean unary = nodes[1] == null;

                if (data[0] == null) {
                    return null;
                }

                if (unary) {
                    return Type.SQL_TIMESTAMP.convertToType(session, data[0],
                            nodes[0].dataType);
                }

                if (data[1] == null) {
                    return null;
                }

                TimestampData date =
                    (TimestampData) Type.SQL_DATE.convertToType(session,
                        data[0], nodes[0].dataType);
                TimeData time = (TimeData) Type.SQL_TIME.convertToType(session,
                    data[1], nodes[1].dataType);

                return new TimestampData(date.getSeconds()
                                         + time.getSeconds(), time.getNanos());
            }
            case FUNC_PI :
                return new Double(Math.PI);

            case FUNC_RAND : {
                if (nodes[0] == null) {
                    return new Double(session.random());
                } else {
                    data[0] = Type.SQL_BIGINT.convertToType(session, data[0],
                            nodes[0].getDataType());

                    long seed = ((Number) data[0]).longValue();

                    return new Double(session.random(seed));
                }
            }
            case FUNC_UUID : {
                if (nodes[0] == null) {
                    UUID uuid = java.util.UUID.randomUUID();
                    long hi   = uuid.getMostSignificantBits();
                    long lo   = uuid.getLeastSignificantBits();

                    return new BinaryData(ArrayUtil.toByteArray(hi, lo),
                                          false);
                } else {
                    if (data[0] == null) {
                        return null;
                    }

                    if (dataType.isBinaryType()) {
                        return new BinaryData(
                            StringConverter.toBinaryUUID((String) data[0]),
                            false);
                    } else {
                        return StringConverter.toStringUUID(
                            ((BinaryData) data[0]).getBytes());
                    }
                }
            }
            case FUNC_UNIX_TIMESTAMP : {
                if (nodes[0] == null) {
                    TimestampData ts = session.getCurrentTimestamp(false);

                    return Long.valueOf(ts.getSeconds());
                } else {
                    if (data[0] == null) {
                        return null;
                    }

                    return Long.valueOf(
                        ((TimestampData) data[0]).getSeconds());
                }
            }
            case FUNC_ACOS : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.acos(d));
            }
            case FUNC_ASIN : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.asin(d));
            }
            case FUNC_ATAN : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.atan(d));
            }
            case FUNC_COS : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.cos(d));
            }
            case FUNC_COT : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);
                double c = 1.0 / java.lang.Math.tan(d);

                return new Double(c);
            }
            case FUNC_DEGREES : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.toDegrees(d));
            }
            case FUNC_SIN : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.sin(d));
            }
            case FUNC_TAN : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.tan(d));
            }
            case FUNC_LOG10 : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.log10(d));
            }
            case FUNC_RADIANS : {
                if (data[0] == null) {
                    return null;
                }

                double d = NumberType.toDouble(data[0]);

                return new Double(java.lang.Math.toRadians(d));
            }

            //
            case FUNC_SIGN : {
                if (data[0] == null) {
                    return null;
                }

                int val =
                    ((NumberType) nodes[0].dataType).compareToZero(data[0]);

                return ValuePool.getInt(val);
            }
            case FUNC_ATAN2 : {
                if (data[0] == null) {
                    return null;
                }

                double a = NumberType.toDouble(data[0]);
                double b = NumberType.toDouble(data[1]);

                return new Double(java.lang.Math.atan2(a, b));
            }
            case FUNC_ASCII : {
                String arg;

                if (data[0] == null) {
                    return null;
                }

                if (nodes[0].dataType.isLobType()) {
                    arg = ((ClobData) data[0]).getSubString(session, 0, 1);
                } else {
                    arg = (String) data[0];
                }

                if (arg.length() == 0) {
                    return null;
                }

                return ValuePool.getInt(arg.charAt(0));
            }
            case FUNC_CHAR :
                if (data[0] == null) {
                    return null;
                }

                data[0] = Type.SQL_INTEGER.convertToType(session, data[0],
                        nodes[0].getDataType());

                int arg = ((Number) data[0]).intValue();

                if (Character.isValidCodePoint(arg)
                        && Character.isValidCodePoint((char) arg)) {
                    return String.valueOf((char) arg);
                }

                throw Error.error(ErrorCode.X_22511);
            case FUNC_ROUNDMAGIC : {
                int offset = 0;

                if (data[0] == null) {
                    return null;
                }

                if (nodes.length > 1) {
                    if (data[1] == null) {
                        return null;
                    }

                    offset = ((Number) data[1]).intValue();
                }

                return ((NumberType) dataType).round(data[0], offset);
            }
            case FUNC_SOUNDEX : {
                if (data[0] == null) {
                    return null;
                }

                String s = (String) data[0];

                return new String(soundex(s), 0, 4);
            }
            case FUNC_BITAND :
            case FUNC_BITOR :
            case FUNC_BITXOR : {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == null) {
                        return null;
                    }
                }

                if (nodes[0].dataType.isIntegralType()) {
                    data[0] = Type.SQL_BIGINT.convertToType(session, data[0],
                            nodes[0].getDataType());
                    data[1] = Type.SQL_BIGINT.convertToType(session, data[1],
                            nodes[1].getDataType());

                    long v = 0;
                    long a = ((Number) data[0]).longValue();
                    long b = ((Number) data[1]).longValue();

                    switch (funcType) {

                        case FUNC_BITAND :
                            v = a & b;
                            break;

                        case FUNC_BITOR :
                            v = a | b;
                            break;

                        case FUNC_BITXOR :
                            v = a ^ b;
                            break;
                    }

                    switch (dataType.typeCode) {

                        case Types.SQL_NUMERIC :
                        case Types.SQL_DECIMAL :
                            return BigDecimal.valueOf(v);

                        case Types.SQL_BIGINT :
                            return ValuePool.getLong(v);

                        case Types.SQL_INTEGER :
                            return ValuePool.getInt((int) v);

                        case Types.SQL_SMALLINT :
                            return ValuePool.getInt((int) v & 0xffff);

                        case Types.TINYINT :
                            return ValuePool.getInt((int) v & 0xff);

                        default :
                            throw Error.error(ErrorCode.X_42561);
                    }
                } else {
                    byte[] a = ((BinaryData) data[0]).getBytes();
                    byte[] b = ((BinaryData) data[1]).getBytes();
                    byte[] v;

                    switch (funcType) {

                        case FUNC_BITAND :
                            v = BitMap.and(a, b);
                            break;

                        case FUNC_BITOR :
                            v = BitMap.or(a, b);
                            break;

                        case FUNC_BITXOR :
                            v = BitMap.xor(a, b);
                            break;

                        default :
                            throw Error.error(ErrorCode.X_42561);
                    }

                    return new BinaryData(v, dataType.precision);
                }
            }
            case FUNC_DIFFERENCE : {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == null) {
                        return null;
                    }
                }

                char[] s1 = soundex((String) data[0]);
                char[] s2 = soundex((String) data[1]);
                int    e  = 0;

                if (s1[0] == s2[0]) {
                    e++;
                }

                if (e == 4) {
                    return ValuePool.getInt(e);
                }

                int js = 1;

                for (int i = 1; i < 4; i++) {
                    for (int j = js; j < 4; j++) {
                        if (s1[j] == s2[i]) {
                            e++;
                            i++;
                            js++;
                        }
                    }
                }

                e = 0;

                return ValuePool.getInt(e);
            }
            case FUNC_HEXTORAW : {
                if (data[0] == null) {
                    return null;
                }

                return dataType.convertToType(session, data[0],
                                              nodes[0].dataType);
            }
            case FUNC_RAWTOHEX : {
                if (data[0] == null) {
                    return null;
                }

                BlobData binary = (BlobData) data[0];
                byte[] bytes = binary.getBytes(session, 0,
                                               (int) binary.length(session));

                return StringConverter.byteArrayToHexString(bytes);
            }
            case FUNC_REPEAT : {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == null) {
                        return null;
                    }
                }

                data[1] = Type.SQL_INTEGER.convertToType(session, data[1],
                        nodes[1].getDataType());

                String       string = (String) data[0];
                int          i      = ((Number) data[1]).intValue();
                StringBuffer sb     = new StringBuffer(string.length() * i);

                while (i-- > 0) {
                    sb.append(string);
                }

                return sb.toString();
            }
            case FUNC_REPLACE : {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == null) {
                        return null;
                    }
                }

                String       string  = (String) data[0];
                String       find    = (String) data[1];
                String       replace = (String) data[2];
                StringBuffer sb      = new StringBuffer();
                int          start   = 0;

                while (true) {
                    int i = string.indexOf(find, start);

                    if (i == -1) {
                        sb.append(string.substring(start));

                        break;
                    }

                    sb.append(string.substring(start, i));
                    sb.append(replace);

                    start = i + find.length();
                }

                return sb.toString();
            }
            case FUNC_LEFT :
            case FUNC_RIGHT : {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == null) {
                        return null;
                    }
                }

                int count = ((Number) data[1]).intValue();

                return ((CharacterType) dataType).substring(session, data[0],
                        0, count, true, funcType == FUNC_RIGHT);
            }
            case FUNC_SPACE : {
                if (data[0] == null) {
                    return null;
                }

                data[0] = Type.SQL_INTEGER.convertToType(session, data[0],
                        nodes[0].getDataType());

                int    count = ((Number) data[0]).intValue();
                char[] array = new char[count];

                ArrayUtil.fillArray(array, 0, ' ');

                return String.valueOf(array);
            }
            case FUNC_REVERSE : {
                if (data[0] == null) {
                    return null;
                }

                StringBuffer sb = new StringBuffer((String) data[0]);

                sb = sb.reverse();

                return sb.toString();
            }
            case FUNC_REGEXP_MATCHES : {
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == null) {
                        return null;
                    }
                }

                if (!data[1].equals(matchPattern)) {
                    matchPattern = (String) data[1];
                    pattern      = Pattern.compile(matchPattern);
                }

                Matcher matcher = pattern.matcher((String) data[0]);

                return matcher.matches() ? Boolean.TRUE
                                         : Boolean.FALSE;
            }
            case FUNC_CRYPT_KEY : {
                byte[] bytes = Crypto.getNewKey((String) data[0],
                                                (String) data[1]);

                return StringConverter.byteArrayToHexString(bytes);
            }
            case FUNC_LOAD_FILE : {
                String fileName = (String) data[0];

                if (fileName == null) {
                    return null;
                }

                switch (dataType.typeCode) {

                    case Types.SQL_CLOB :
                        return session.sessionData.createClobFromFile(fileName,
                                (String) data[1]);

                    case Types.SQL_BLOB :
                    default :
                        return session.sessionData.createBlobFromFile(
                            fileName);
                }
            }
            case FUNC_LPAD :
            case FUNC_RPAD :
                if (data[0] == null || data[1] == null) {
                    return null;
                }

                String value;

                if (nodes[0].dataType.isCharacterType()
                        && !nodes[0].dataType.isLobType()) {
                    value = (String) data[0];
                } else {
                    value = nodes[0].dataType.convertToString(data[0]);
                }

                int length = ((Integer) Type.SQL_INTEGER.convertToType(session,
                    data[1], nodes[1].dataType)).intValue();
                String pad = " ";

                if (nodes[2] != null) {
                    pad = nodes[2].dataType.convertToString(data[2]);

                    if (pad.length() == 0) {
                        pad = " ";
                    }
                }

                value = (String) Type.SQL_VARCHAR.trim(session, value, ' ',
                                                       true, true);
                value = StringUtil.toPaddedString(value, length, pad,
                                                  funcType == FUNC_RPAD);

                if (dataType.isLobType()) {
                    return dataType.convertToType(session, value,
                                                  Type.SQL_VARCHAR);
                } else {
                    return value;
                }
            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "FunctionCustom");
        }
    }

    public void resolveTypes(Session session, Expression parent) {

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                nodes[i].resolveTypes(session, this);
            }
        }

        switch (funcType) {

            case FUNC_POSITION_CHAR :
            case FUNC_EXTRACT :
            case FUNC_TRIM_CHAR :
            case FUNC_OVERLAY_CHAR :
                super.resolveTypes(session, parent);

                return;

            case FUNC_DATABASE :
                dataType = Type.SQL_VARCHAR_DEFAULT;

                return;

            case FUNC_DATABASE_NAME :
                dataType = Type.SQL_VARCHAR_DEFAULT;

                return;

            case FUNC_ISAUTOCOMMIT :
            case FUNC_ISREADONLYSESSION :
            case FUNC_ISREADONLYDATABASE :
            case FUNC_ISREADONLYDATABASEFILES :
                dataType = Type.SQL_BOOLEAN;

                return;

            case FUNC_ISOLATION_LEVEL :
            case FUNC_SESSION_ISOLATION_LEVEL :
            case FUNC_DATABASE_ISOLATION_LEVEL :
            case FUNC_TRANSACTION_CONTROL :
            case FUNC_DATABASE_VERSION :
                dataType = Type.SQL_VARCHAR_DEFAULT;

                return;

            case FUNC_TIMEZONE :
            case FUNC_SESSION_TIMEZONE :
            case FUNC_DATABASE_TIMEZONE :
                dataType = Type.SQL_INTERVAL_HOUR_TO_MINUTE;

                return;

            case FUNC_SESSION_ID :
            case FUNC_ACTION_ID :
            case FUNC_TRANSACTION_ID :
            case FUNC_TRANSACTION_SIZE :
            case FUNC_LOB_ID :
            case FUNC_IDENTITY :
                dataType = Type.SQL_BIGINT;

                return;

            case FUNC_SEQUENCE_ARRAY : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = nodes[1].dataType;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = nodes[0].dataType;
                }

                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_INTEGER;
                    nodes[1].dataType = Type.SQL_INTEGER;
                }

                if (nodes[0].dataType.isNumberType()) {
                    if (nodes[2].dataType == null) {
                        nodes[2].dataType = nodes[0].dataType;
                    }
                } else if (nodes[0].dataType.isDateTimeType()) {
                    if (nodes[2].dataType == null) {
                        throw Error.error(ErrorCode.X_42561);
                    }

                    if (!nodes[2].dataType.isIntervalType()) {
                        throw Error.error(ErrorCode.X_42561);
                    }
                }

                dataType =
                    new ArrayType(nodes[0].getDataType(),
                                  ArrayType.defaultLargeArrayCardinality);

                return;
            }
            case FUNC_DATEADD : {
                int part;

                if (!nodes[0].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                if ("yy".equalsIgnoreCase((String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_YEAR;
                } else if ("mm".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_MONTH;
                } else if ("dd".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_DAY;
                } else if ("hh".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_HOUR;
                } else if ("mi".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_MINUTE;
                } else if ("ss".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_SECOND;
                } else if ("ms".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_FRAC_SECOND;
                } else {
                    throw Error.error(ErrorCode.X_42566,
                                      (String) nodes[0].valueData);
                }

                nodes[0].valueData = ValuePool.getInt(part);
                nodes[0].dataType  = Type.SQL_INTEGER;
                funcType           = FUNC_TIMESTAMPADD;
            }

            // fall through
            case FUNC_TIMESTAMPADD :
                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_BIGINT;
                }

                if (nodes[2].dataType == null) {
                    nodes[2].dataType = Type.SQL_TIMESTAMP;
                }

                if (!nodes[1].dataType.isIntegralType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                if (nodes[2].dataType.typeCode != Types.SQL_DATE
                        && nodes[2].dataType.typeCode != Types.SQL_TIMESTAMP
                        && nodes[2].dataType.typeCode
                           != Types.SQL_TIMESTAMP_WITH_TIME_ZONE) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = nodes[2].dataType;

                return;

            case FUNC_DATEDIFF : {
                int part;

                if (!nodes[0].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42563);
                }

                if ("yy".equalsIgnoreCase((String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_YEAR;
                } else if ("mm".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_MONTH;
                } else if ("dd".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_DAY;
                } else if ("hh".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_HOUR;
                } else if ("mi".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_MINUTE;
                } else if ("ss".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_SECOND;
                } else if ("ms".equalsIgnoreCase(
                        (String) nodes[0].valueData)) {
                    part = Tokens.SQL_TSI_FRAC_SECOND;
                } else {
                    throw Error.error(ErrorCode.X_22511,
                                      (String) nodes[0].valueData);
                }

                nodes[0].valueData = ValuePool.getInt(part);
                nodes[0].dataType  = Type.SQL_INTEGER;
                funcType           = FUNC_TIMESTAMPDIFF;
            }

            // fall through
            case FUNC_TIMESTAMPDIFF : {
                if (nodes[1].dataType == null) {
                    nodes[1].dataType = nodes[2].dataType;
                }

                if (nodes[2].dataType == null) {
                    nodes[2].dataType = nodes[1].dataType;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_TIMESTAMP;
                    nodes[2].dataType = Type.SQL_TIMESTAMP;
                }

                switch (nodes[1].dataType.typeCode) {

                    case Types.SQL_DATE :
                        if (nodes[2].dataType.typeCode != Types.SQL_DATE) {
                            throw Error.error(ErrorCode.X_42563);
                        }

                        switch (((Integer) nodes[0].valueData).intValue()) {

                            case Tokens.SQL_TSI_DAY :
                            case Tokens.SQL_TSI_WEEK :
                            case Tokens.SQL_TSI_MONTH :
                            case Tokens.SQL_TSI_QUARTER :
                            case Tokens.SQL_TSI_YEAR :
                                break;

                            default :
                                throw Error.error(ErrorCode.X_42563);
                        }
                        break;

                    case Types.SQL_TIMESTAMP :
                    case Types.SQL_TIMESTAMP_WITH_TIME_ZONE :
                        if (nodes[2].dataType.typeCode != Types.SQL_TIMESTAMP
                                && nodes[2].dataType.typeCode
                                   != Types.SQL_TIMESTAMP_WITH_TIME_ZONE) {
                            throw Error.error(ErrorCode.X_42563);
                        }
                        break;

                    default :
                        throw Error.error(ErrorCode.X_42563);
                }

                dataType = Type.SQL_BIGINT;

                return;
            }
            case FUNC_ROUND :
            case FUNC_TRUNC : {
                boolean single = nodes.length == 1 || nodes[1] == null;

                if (nodes[0].dataType == null) {
                    if (single) {
                        nodes[0].dataType = Type.SQL_DECIMAL;
                    } else {
                        if (nodes[1].dataType == null) {
                            nodes[1].dataType = Type.SQL_INTEGER;
                        }

                        if (nodes[1].dataType.isNumberType()) {
                            nodes[0].dataType = Type.SQL_DECIMAL;
                        } else {
                            nodes[0].dataType = Type.SQL_TIMESTAMP;
                        }
                    }
                }

                if (nodes[0].dataType.isDateTimeType()) {
                    if (!single) {
                        if (!nodes[1].dataType.isCharacterType()) {
                            throw Error.error(ErrorCode.X_42566);
                        }
                    }

                    dataType = nodes[0].dataType;

                    break;
                } else if (nodes[0].dataType.isNumberType()) {

                    //
                } else {
                    throw Error.error(ErrorCode.X_42561);
                }
            }

            // fall through
            case FUNC_TRUNCATE : {
                Number offset = null;

                if (nodes[0].dataType == null) {
                    throw Error.error(ErrorCode.X_42567);
                }

                if (!nodes[0].dataType.isNumberType()) {
                    throw Error.error(ErrorCode.X_42563);
                }

                if (nodes[1] == null) {
                    nodes[1] = new ExpressionValue(ValuePool.INTEGER_0,
                                                   Type.SQL_INTEGER);
                    offset = ValuePool.INTEGER_0;
                } else {
                    if (nodes[1].dataType == null) {
                        nodes[1].dataType = Type.SQL_INTEGER;
                    } else if (!nodes[1].dataType.isIntegralType()) {
                        throw Error.error(ErrorCode.X_42563);
                    }

                    if (nodes[1].opType == OpTypes.VALUE) {
                        offset = (Number) nodes[1].getValue(session);
                    }
                }

                dataType = nodes[0].dataType;

                if (offset != null) {
                    int scale = offset.intValue();

                    if (scale < 0) {
                        scale = 0;
                    } else if (scale > dataType.scale) {
                        scale = dataType.scale;
                    }

                    if (dataType.typeCode == Types.SQL_DECIMAL
                            || dataType.typeCode == Types.SQL_NUMERIC) {
                        if (scale != dataType.scale) {
                            dataType = new NumberType(dataType.typeCode,
                                                      dataType.precision
                                                      - dataType.scale
                                                      + scale, scale);
                        }
                    }
                }

                return;
            }
            case FUNC_TO_CHAR : {
                if (nodes[0].dataType == null) {
                    nodes[1].dataType = Type.SQL_TIMESTAMP;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                if (!nodes[1].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42563);
                }

                if (!nodes[0].dataType.isDateTimeType()) {
                    throw Error.error(ErrorCode.X_42563);
                }

                // fixed maximum as format is a variable
                dataType = CharacterType.getCharacterType(Types.SQL_VARCHAR,
                        40);

                return;
            }
            case FUNC_TO_DATE :
            case FUNC_TO_TIMESTAMP : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                if (!nodes[0].dataType.isCharacterType()
                        || !nodes[1].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42567);
                }

                dataType = funcType == FUNC_TO_DATE ? Type.SQL_DATE
                                                    : Type.SQL_TIMESTAMP;

                return;
            }
            case FUNC_TIMESTAMP : {
                Type argType = nodes[0].dataType;

                if (nodes[1] == null) {
                    if (argType == null) {
                        argType = nodes[0].dataType = Type.SQL_VARCHAR_DEFAULT;
                    }

                    if (argType.isCharacterType()
                            || argType.typeCode == Types.SQL_TIMESTAMP
                            || argType.typeCode
                               == Types.SQL_TIMESTAMP_WITH_TIME_ZONE) {}
                    else {
                        throw Error.error(ErrorCode.X_42561);
                    }
                } else {
                    if (argType == null) {
                        if (nodes[1].dataType == null) {
                            argType = nodes[0].dataType = nodes[1].dataType =
                                Type.SQL_VARCHAR_DEFAULT;
                        } else {
                            if (nodes[1].dataType.isCharacterType()) {
                                argType = nodes[0].dataType =
                                    Type.SQL_VARCHAR_DEFAULT;
                            } else {
                                argType = nodes[0].dataType = Type.SQL_DATE;
                            }
                        }
                    }

                    if (nodes[1].dataType == null) {
                        if (argType.isCharacterType()) {
                            nodes[1].dataType = Type.SQL_VARCHAR_DEFAULT;
                        } else if (argType.typeCode == Types.SQL_DATE) {
                            nodes[1].dataType = Type.SQL_TIME;
                        }
                    }

                    if ((argType.typeCode == Types.SQL_DATE && nodes[1]
                            .dataType.typeCode == Types.SQL_TIME) || argType
                                .isCharacterType() && nodes[1].dataType
                                .isCharacterType()) {}
                    else {
                        throw Error.error(ErrorCode.X_42561);
                    }
                }

                dataType = Type.SQL_TIMESTAMP;

                return;
            }
            case FUNC_PI :
                dataType = Type.SQL_DOUBLE;
                break;

            case FUNC_UUID : {
                if (nodes[0] == null) {
                    dataType = Type.SQL_BINARY_16;
                } else {
                    if (nodes[0].dataType == null) {
                        nodes[0].dataType = Type.SQL_VARCHAR;
                        dataType          = Type.SQL_BINARY_16;
                    } else if (nodes[0].dataType.isCharacterType()) {
                        dataType = Type.SQL_BINARY_16;
                    } else if (nodes[0].dataType.isBinaryType()
                               && !nodes[0].dataType.isLobType()) {
                        dataType = Type.SQL_CHAR_16;
                    } else {
                        throw Error.error(ErrorCode.X_42563);
                    }
                }

                break;
            }
            case FUNC_UNIX_TIMESTAMP : {
                if (nodes[0] != null) {
                    if (nodes[0].dataType == null) {
                        nodes[0].dataType = Type.SQL_TIMESTAMP;
                    } else if (!nodes[0].dataType.isDateTimeType()
                               || nodes[0].dataType.typeCode == Types.SQL_TIME
                               || nodes[0].dataType.typeCode
                                  == Types.SQL_TIME_WITH_TIME_ZONE) {
                        throw Error.error(ErrorCode.X_42563);
                    }
                }

                dataType = Type.SQL_BIGINT;

                break;
            }
            case FUNC_RAND : {
                if (nodes[0] != null) {
                    if (nodes[0].dataType == null) {
                        nodes[0].dataType = Type.SQL_BIGINT;
                    } else if (!nodes[0].dataType.isExactNumberType()) {
                        throw Error.error(ErrorCode.X_42563);
                    }
                }

                dataType = Type.SQL_DOUBLE;

                break;
            }
            case FUNC_ACOS :
            case FUNC_ASIN :
            case FUNC_ATAN :
            case FUNC_COS :
            case FUNC_COT :
            case FUNC_DEGREES :
            case FUNC_SIN :
            case FUNC_TAN :
            case FUNC_LOG10 :
            case FUNC_RADIANS :
            case FUNC_ROUNDMAGIC : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_DOUBLE;
                }

                if (!nodes[0].dataType.isNumberType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = Type.SQL_DOUBLE;

                break;
            }
            case FUNC_SIGN : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_DOUBLE;
                }

                if (!nodes[0].dataType.isNumberType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = Type.SQL_INTEGER;

                break;
            }
            case FUNC_ATAN2 : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_DOUBLE;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_DOUBLE;
                }

                if (!nodes[0].dataType.isNumberType()
                        || !nodes[1].dataType.isNumberType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = Type.SQL_DOUBLE;

                break;
            }
            case FUNC_SOUNDEX : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR;
                }

                if (!nodes[0].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = CharacterType.getCharacterType(Types.SQL_VARCHAR,
                        4);

                break;
            }
            case FUNC_BITAND :
            case FUNC_BITOR :
            case FUNC_BITXOR : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = nodes[1].dataType;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = nodes[0].dataType;
                }

                for (int i = 0; i < nodes.length; i++) {
                    if (nodes[i].dataType == null) {
                        nodes[i].dataType = Type.SQL_INTEGER;
                    }
                }

                dataType =
                    nodes[0].dataType.getAggregateType(nodes[1].dataType);

                switch (dataType.typeCode) {

                    case Types.SQL_BIGINT :
                    case Types.SQL_INTEGER :
                    case Types.SQL_SMALLINT :
                    case Types.TINYINT :
                        break;

                    case Types.SQL_BIT :
                    case Types.SQL_BIT_VARYING :
                        break;

                    default :
                        throw Error.error(ErrorCode.X_42561);
                }

                break;
            }
            case FUNC_ASCII : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR;
                }

                if (!nodes[0].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = Type.SQL_INTEGER;

                break;
            }
            case FUNC_CHAR : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_INTEGER;
                }

                if (!nodes[0].dataType.isExactNumberType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = CharacterType.getCharacterType(Types.SQL_VARCHAR,
                        1);

                break;
            }
            case FUNC_DIFFERENCE : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_VARCHAR;
                }

                dataType = Type.SQL_INTEGER;

                break;
            }
            case FUNC_HEXTORAW : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR;
                }

                if (!nodes[0].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = nodes[0].dataType.precision == 0
                           ? Type.SQL_VARBINARY_DEFAULT
                           : BinaryType.getBinaryType(
                               Types.SQL_VARBINARY,
                               nodes[0].dataType.precision / 2);

                break;
            }
            case FUNC_RAWTOHEX : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARBINARY;
                }

                if (!nodes[0].dataType.isBinaryType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = nodes[0].dataType.precision == 0
                           ? Type.SQL_VARCHAR_DEFAULT
                           : CharacterType.getCharacterType(Types.SQL_VARCHAR,
                           nodes[0].dataType.precision * 2);

                break;
            }
            case FUNC_REPEAT : {
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR;
                }

                boolean isChar = nodes[0].dataType.isCharacterType();

                if (!isChar && !nodes[0].dataType.isBinaryType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                if (!nodes[1].dataType.isExactNumberType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = isChar ? (Type) Type.SQL_VARCHAR_DEFAULT
                                  : (Type) Type.SQL_VARBINARY_DEFAULT;

                break;
            }
            case FUNC_REPLACE : {
                for (int i = 0; i < nodes.length; i++) {
                    if (nodes[i].dataType == null) {
                        nodes[i].dataType = Type.SQL_VARCHAR;
                    } else if (!nodes[i].dataType.isCharacterType()) {
                        throw Error.error(ErrorCode.X_42561);
                    }
                }

                dataType = Type.SQL_VARCHAR_DEFAULT;

                break;
            }
            case FUNC_LEFT :
            case FUNC_RIGHT :
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR;
                }

                if (!nodes[0].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_INTEGER;
                }

                if (!nodes[1].dataType.isExactNumberType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = nodes[0].dataType.precision == 0
                           ? Type.SQL_VARCHAR_DEFAULT
                           : ((CharacterType) nodes[0].dataType)
                               .getCharacterType(nodes[0].dataType.precision);
                break;

            case FUNC_SPACE :
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_INTEGER;
                }

                if (!nodes[0].dataType.isIntegralType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = Type.SQL_VARCHAR_DEFAULT;
                break;

            case FUNC_REVERSE :
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                dataType = nodes[0].dataType;

                if (!dataType.isCharacterType() || dataType.isLobType()) {
                    throw Error.error(ErrorCode.X_42561);
                }
                break;

            case FUNC_REGEXP_MATCHES :
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                if (!nodes[0].dataType.isCharacterType()
                        || !nodes[1].dataType.isCharacterType()
                        || nodes[1].dataType.isLobType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                dataType = Type.SQL_BOOLEAN;
                break;

            case FUNC_CRYPT_KEY :
                for (int i = 0; i < nodes.length; i++) {
                    if (nodes[i].dataType == null) {
                        nodes[i].dataType = Type.SQL_VARCHAR;
                    } else if (!nodes[i].dataType.isCharacterType()) {
                        throw Error.error(ErrorCode.X_42561);
                    }
                }

                dataType = Type.SQL_VARCHAR_DEFAULT;
                break;

            case FUNC_LOAD_FILE :
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                if (!nodes[0].dataType.isCharacterType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                if (nodes[1] == null) {
                    dataType = Type.SQL_BLOB;
                } else {
                    dataType = Type.SQL_CLOB;

                    if (nodes[1].dataType == null
                            || !nodes[1].dataType.isCharacterType()) {
                        throw Error.error(ErrorCode.X_42561);
                    }
                }
                break;

            case FUNC_LPAD :
            case FUNC_RPAD :
                if (nodes[0].dataType == null) {
                    nodes[0].dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                if (nodes[1].dataType == null) {
                    nodes[1].dataType = Type.SQL_INTEGER;
                }

                if (!nodes[1].dataType.isIntegralType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                if (nodes[2] != null) {
                    if (nodes[2].dataType == null) {
                        nodes[2].dataType = Type.SQL_VARCHAR_DEFAULT;
                    }

                    if (!nodes[2].dataType.isCharacterType()) {
                        throw Error.error(ErrorCode.X_42561);
                    }
                }

                dataType = nodes[0].dataType;

                if (dataType.typeCode != Types.SQL_CLOB) {
                    dataType = Type.SQL_VARCHAR_DEFAULT;
                }

                if (nodes[1].opType == OpTypes.VALUE) {
                    Number value = (Number) nodes[1].getValue(session);

                    if (value != null) {
                        dataType = ((CharacterType) dataType).getCharacterType(
                            value.longValue());
                    }
                }
                break;

            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "FunctionCustom");
        }
    }

    public String getSQL() {

        switch (funcType) {

            case FUNC_POSITION_CHAR : {

                // LOCATE
                StringBuffer sb = new StringBuffer(Tokens.T_LOCATE).append(
                    Tokens.T_OPENBRACKET).append(nodes[0].getSQL())         //
                    .append(Tokens.T_COMMA).append(nodes[1].getSQL());      //

                if (nodes.length > 3 && nodes[3] != null) {
                    sb.append(Tokens.T_COMMA).append(nodes[3].getSQL());    //
                }

                sb.append(Tokens.T_CLOSEBRACKET).toString();

                return sb.toString();
            }
            case FUNC_LPAD :
            case FUNC_RPAD : {
                StringBuffer sb = new StringBuffer(name);

                sb.append(Tokens.T_OPENBRACKET).append(nodes[0].getSQL());
                sb.append(Tokens.T_COMMA).append(nodes[1].getSQL());

                if (nodes[2] != null) {
                    sb.append(Tokens.T_COMMA).append(nodes[2].getSQL());
                }

                sb.append(Tokens.T_CLOSEBRACKET).toString();

                return sb.toString();
            }
            case FUNC_EXTRACT :
            case FUNC_TRIM_CHAR :
            case FUNC_OVERLAY_CHAR :
                return super.getSQL();

            case FUNC_DATABASE :
            case FUNC_DATABASE_NAME :
            case FUNC_ISAUTOCOMMIT :
            case FUNC_ISREADONLYSESSION :
            case FUNC_ISREADONLYDATABASE :
            case FUNC_ISREADONLYDATABASEFILES :
            case FUNC_ISOLATION_LEVEL :
            case FUNC_SESSION_ISOLATION_LEVEL :
            case FUNC_DATABASE_ISOLATION_LEVEL :
            case FUNC_TRANSACTION_CONTROL :
            case FUNC_TIMEZONE :
            case FUNC_SESSION_TIMEZONE :
            case FUNC_DATABASE_TIMEZONE :
            case FUNC_DATABASE_VERSION :
            case FUNC_PI :
            case FUNC_IDENTITY :
            case FUNC_SESSION_ID :
            case FUNC_ACTION_ID :
            case FUNC_TRANSACTION_ID :
            case FUNC_TRANSACTION_SIZE :
                return new StringBuffer(name).append(
                    Tokens.T_OPENBRACKET).append(
                    Tokens.T_CLOSEBRACKET).toString();

            case FUNC_TIMESTAMPADD : {
                String token = Tokens.getSQLTSIString(
                    ((Number) nodes[0].getValue(null)).intValue());

                return new StringBuffer(Tokens.T_TIMESTAMPADD).append(
                    Tokens.T_OPENBRACKET).append(token)                     //
                    .append(Tokens.T_COMMA).append(nodes[1].getSQL())       //
                    .append(Tokens.T_COMMA).append(nodes[2].getSQL())       //
                    .append(Tokens.T_CLOSEBRACKET).toString();
            }
            case FUNC_TIMESTAMPDIFF : {
                String token = Tokens.getSQLTSIString(
                    ((Number) nodes[0].getValue(null)).intValue());

                return new StringBuffer(Tokens.T_TIMESTAMPDIFF).append(
                    Tokens.T_OPENBRACKET).append(token)                     //
                    .append(Tokens.T_COMMA).append(nodes[1].getSQL())       //
                    .append(Tokens.T_COMMA).append(nodes[2].getSQL())       //
                    .append(Tokens.T_CLOSEBRACKET).toString();
            }
            case FUNC_UNIX_TIMESTAMP :
            case FUNC_RAND : {
                StringBuffer sb = new StringBuffer(name).append('(');

                if (nodes[0] != null) {
                    sb.append(nodes[0].getSQL());
                }

                sb.append(')');

                return sb.toString();
            }
            case FUNC_LOAD_FILE :
            case FUNC_ROUND :
            case FUNC_TRUNC :
            case FUNC_TRUNCATE : {
                StringBuffer sb = new StringBuffer(name).append('(');

                sb.append(nodes[0].getSQL());

                if (nodes[0] != null) {
                    sb.append(',').append(nodes[0].getSQL());
                }

                sb.append(')');

                return sb.toString();
            }
            case FUNC_ASCII :
            case FUNC_ACOS :
            case FUNC_ASIN :
            case FUNC_ATAN :
            case FUNC_CHAR :
            case FUNC_COS :
            case FUNC_COT :
            case FUNC_DEGREES :
            case FUNC_SIN :
            case FUNC_TAN :
            case FUNC_LOG10 :
            case FUNC_RADIANS :
            case FUNC_ROUNDMAGIC :
            case FUNC_SIGN :
            case FUNC_SOUNDEX :
            case FUNC_SPACE :
            case FUNC_REVERSE :
            case FUNC_HEXTORAW :
            case FUNC_RAWTOHEX :
            case FUNC_LOB_ID : {
                return new StringBuffer(name).append('(')                   //
                    .append(nodes[0].getSQL()).append(')').toString();
            }
            case FUNC_ATAN2 :
            case FUNC_BITAND :
            case FUNC_BITOR :
            case FUNC_BITXOR :
            case FUNC_DIFFERENCE :
            case FUNC_REPEAT :
            case FUNC_LEFT :
            case FUNC_RIGHT :
            case FUNC_CRYPT_KEY :
            case FUNC_TIMESTAMP :
            case FUNC_TO_CHAR :
            case FUNC_TO_DATE :
            case FUNC_TO_TIMESTAMP :
            case FUNC_REGEXP_MATCHES : {
                return new StringBuffer(name).append('(')                   //
                    .append(nodes[0].getSQL()).append(Tokens.T_COMMA)       //
                    .append(nodes[1].getSQL()).append(')').toString();
            }
            case FUNC_SEQUENCE_ARRAY :
            case FUNC_REPLACE : {
                return new StringBuffer(name).append('(')                   //
                    .append(nodes[0].getSQL()).append(Tokens.T_COMMA)       //
                    .append(nodes[1].getSQL()).append(Tokens.T_COMMA)       //
                    .append(nodes[2].getSQL()).append(')').toString();
            }
            default :
                return super.getSQL();
        }
    }

    /**
     * Returns a four character code representing the sound of the given
     * <code>String</code>. Non-ASCCI characters in the
     * input <code>String</code> are ignored. <p>
     *
     * This method was rewritten for HSQLDB to comply with the description at
     * <a href="http://www.archives.gov/genealogy/census/soundex.html">
     * http://www.archives.gov/genealogy/census/soundex.html </a>.<p>
     * @param s the <code>String</code> for which to calculate the 4 character
     *      <code>SOUNDEX</code> value
     * @return the 4 character <code>SOUNDEX</code> value for the given
     *      <code>String</code>
     */
    public static char[] soundex(String s) {

        if (s == null) {
            return null;
        }

        s = s.toUpperCase(Locale.ENGLISH);

        int    len       = s.length();
        char[] b         = new char[] {
            '0', '0', '0', '0'
        };
        char   lastdigit = '0';

        for (int i = 0, j = 0; i < len && j < 4; i++) {
            char c = s.charAt(i);
            char newdigit;

            if ("AEIOUY".indexOf(c) != -1) {
                newdigit = '7';
            } else if (c == 'H' || c == 'W') {
                newdigit = '8';
            } else if ("BFPV".indexOf(c) != -1) {
                newdigit = '1';
            } else if ("CGJKQSXZ".indexOf(c) != -1) {
                newdigit = '2';
            } else if (c == 'D' || c == 'T') {
                newdigit = '3';
            } else if (c == 'L') {
                newdigit = '4';
            } else if (c == 'M' || c == 'N') {
                newdigit = '5';
            } else if (c == 'R') {
                newdigit = '6';
            } else {
                continue;
            }

            if (j == 0) {
                b[j++]    = c;
                lastdigit = newdigit;
            } else if (newdigit <= '6') {
                if (newdigit != lastdigit) {
                    b[j++]    = newdigit;
                    lastdigit = newdigit;
                }
            } else if (newdigit == '7') {
                lastdigit = newdigit;
            }
        }

        return b;
    }
}
