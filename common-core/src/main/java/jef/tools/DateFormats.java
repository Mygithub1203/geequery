/*
 * JEF - Copyright 2009-2010 Jiyi (mr.jiyi@gmail.com)
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
package jef.tools;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * 用于提供各种线程安全的时间日期格式
 * <li>G 年代标志符</li>
 * <li>y 年</li>
 * <li>M 月</li>
 * <li>d 日</li>
 * <li>h 时 在上午或下午 (1~12)</li>
 * <li>H 时 在一天中 (0~23)</li>
 * <li>m 分</li>
 * <li>s 秒</li>
 * <li>S 毫秒</li>
 * <li>E 星期</li>
 * <li>D 一年中的第几天</li>
 * <li>F 一月中第几个星期几</li>
 * <li>w 一年中第几个星期</li>
 * <li>W 一月中第几个星期</li>
 * <li>a 上午 / 下午 标记符</li>
 * <li>k 时 在一天中 (1~24)</li>
 * <li>K 时 在上午或下午 (0~11)</li>
 * <li>z 时区</li>
 * 
 * @author Jiyi
 * 
 */
public abstract class DateFormats {
	private static final String[] TIME_ZONES = new String[] { "Etc/GMT+12", "Pacific/Midway", "US/Hawaii", "US/Alaska", "US/Pacific", "US/Arizona", "US/Central", "America/New_York", "PRT", "America/Araguaina",
			"Atlantic/South_Georgia", "Atlantic/Azores", "GMT", "Etc/GMT-1", "Etc/GMT-2", "Europe/Moscow", "Etc/GMT-4", "IST", "Etc/GMT-6", "Etc/GMT-7", "Asia/Shanghai", "Asia/Tokyo", "Australia/ACT", "Etc/GMT-11",
			"Etc/GMT-12", "Pacific/Apia", "Pacific/Kiritimati" };

	// 支持 yyyy-m-d 格式的
	// 非严格模式正则
	public static final String DATE_CS_REGEXP = "((^((1[8-9]\\d{2})|([2-9]\\d{3}))([-\\/\\._])(10|12|0?[13578])([-\\/\\._])(3[01]|[12][0-9]|0?[1-9])$)|(^((1[8-9]\\d{2})|([2-9]\\d{3}))([-\\/\\._])(11|0?[469])([-\\/\\._])(30|[12][0-9]|0?[1-9])$)|(^((1[8-9]\\d{2})|([2-9]\\d{3}))([-\\/\\._])(0?2)([-\\/\\._])(2[0-8]|1[0-9]|0?[1-9])$)|(^([2468][048]00)([-\\/\\._])(0?2)([-\\/\\._])(29)$)|(^([3579][26]00)([-\\/\\._])(0?2)([-\\/\\._])(29)$)|(^([1][89][0][48])([-\\/\\._])(0?2)([-\\/\\._])(29)$)|(^([2-9][0-9][0][48])([-\\/\\._])(0?2)([-\\/\\._])(29)$)|(^([1][89][2468][048])([-\\/\\._])(0?2)([-\\/\\._])(29)$)|(^([2-9][0-9][2468][048])([-\\/\\._])(0?2)([-\\/\\._])(29)$)|(^([1][89][13579][26])([-\\/\\._])(0?2)([-\\/\\._])(29)$)|(^([2-9][0-9][13579][26])([-\\/\\._])(0?2)([-\\/\\._])(29)$))";
	// 严格模式正则
	public static final String DATE_CS_REGEXP_STRICT = "(([0-9]{3}[1-9]|[0-9]{2}[1-9][0-9]{1}|[0-9]{1}[1-9][0-9]{2}|[1-9][0-9]{3})-(((0[13578]|1[02])-(0[1-9]|[12][0-9]|3[01]))|((0[469]|11)-(0[1-9]|[12][0-9]|30))|(02-(0[1-9]|[1][0-9]|2[0-8]))))|((([0-9]{2})(0[48]|[2468][048]|[13579][26])|((0[48]|[2468][048]|[3579][26])00))-02-29)";

	/** 日期格式：美式日期 MM/DD/YYYY */
	public static final TLDateFormat DATE_US = new TLDateFormat("MM/dd/yyyy");

	/** 日期格式：美式日期+时间 MM/DD/YYYY HH:MI:SS */
	public static final TLDateFormat DATE_TIME_US = new TLDateFormat("MM/dd/yyyy HH:mm:ss");

	/** 日期格式：中式日期 YYYY-MM-DD */
	public static final TLDateFormat DATE_CS = new TLDateFormat("yyyy-MM-dd");

	/** 日期格式：日期+时间 YYYY/MM/DD */
	public static final TLDateFormat DATE_CS2 = new TLDateFormat("yyyy/MM/dd");

	/** 日期格式：日期+时间 YYYY-MM-DD HH:MI:SS */
	public static final TLDateFormat DATE_TIME_CS = new TLDateFormat("yyyy-MM-dd HH:mm:ss");

	/** 日期格式：日期+时间 YYYY/MM/DD HH:MI:SS */
	public static final TLDateFormat DATE_TIME_CS2 = new TLDateFormat("yyyy/MM/dd HH:mm:ss");

	/** 日期格式：中式日期时间（到分） YYYY-MM-DD HH:MI */
	public static final TLDateFormat DATE_TIME_ROUGH = new TLDateFormat("yyyy-MM-dd HH:mm");

	/** 日期格式：中式日期+时间戳 YYYY-MM-DD HH:MI:SS.SSS */
	public static final TLDateFormat TIME_STAMP_CS = new TLDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	/** 日期格式：仅时间 HH.MI.SS */
	public static final TLDateFormat TIME_ONLY = new TLDateFormat("HH:mm:ss");

	/** 日期格式：日期紧凑 YYYYMMDD */
	public static final TLDateFormat DATE_SHORT = new TLDateFormat("yyyyMMdd");

	/** 日期格式：日期时间紧凑 YYYYMMDDHHMISS */
	public static final TLDateFormat DATE_TIME_SHORT_14 = new TLDateFormat("yyyyMMddHHmmss");

	/** 日期格式：日期时间紧凑 YYYYMMDDHHMI */
	public static final TLDateFormat DATE_TIME_SHORT_12 = new TLDateFormat("yyyyMMddHHmm");

	/** 日期格式：yyyyMM */
	public static final TLDateFormat YAER_MONTH = new TLDateFormat("yyyyMM");

	///////////////// 以下是别名，最常用的日期格式加上别名////////////////////
	/** 日期格式：中式日期 YYYY-MM-DD */
	public static final TLDateFormat YYYY_MM_DD = DATE_CS;

	/** 日期格式：日期+时间 YYYY-MM-DD HH:MI:SS */
	public static final TLDateFormat YYYY_MM_DD$HH_MI_SS = DATE_TIME_CS;

	/** 日期格式：日期紧凑 yyyyMMdd */
	public static final TLDateFormat YYYYMMDD = DATE_SHORT;

	/** 日期格式：仅时间 HH.MI.SS */
	public static final TLDateFormat HH_MI_SS = TIME_ONLY;

	
	/**
	 * 线程安全的日期格式转换，同时支持全部的27个UTC。
	 * 注意本类比较重，建议设计为全局变量或静态变量，不要频繁的进行构造和回收。
	 * @author jiyi
	 */
	public static final class TLDateFormat extends java.lang.ThreadLocal<SuperDataFormat> {
		private String pattern;

		public TLDateFormat(String p) {
			this.pattern = p;
		}

		@Override
		protected SuperDataFormat initialValue() {
			return new SuperDataFormat(pattern);
		}

		/**
		 * 格式化日期，如果传入null返回null
		 * 
		 * @param date
		 * @return text
		 */
		public String format(Date date) {
			return date == null ? null : get().format(date);
		}

		/**
		 * 格式化日期，按指定的时区进行输出
		 * 
		 * @param date
		 * @param zone
		 * @return text
		 */
		public String format(Date date, TimeZone zone) {
			return format(date, zone.getRawOffset() / 3600000);
		}

		/**
		 * 格式化日期，按指定的时区进行输出
		 * 
		 * @param date
		 * @param utcOffset 相对国际原子时的时差，从-12到+14(中国为8)
		 * @return 指定时区内的时间
		 */
		public String format(Date date, int utcOffset) {
			return format0(date, utcOffset + 12);
		}

		/*
		 * 支持时区的格式化
		 */
		private String format0(Date date, int offset) {
			return get().get(offset).format(date);
		}

		/**
		 * 解析时间，如果为空返回null
		 * 
		 * @param text
		 * @return Date parsed.
		 * @throws IllegalArgumentException 格式不对抛出异常
		 */
		public Date parse(String text) throws IllegalArgumentException {
			if (StringUtils.isEmpty(text)) {
				return null;
			}
			try {
				return get().parse(text);
			} catch (ParseException e) {
				throw new IllegalArgumentException("Invalid date:" + text, e);
			}
		}

		/**
		 * 解析日期
		 * 
		 * @param text 时间文字
		 * @param zone 时区
		 * @return
		 * @throws IllegalArgumentException
		 */
		public Date parse(String text, TimeZone zone) throws IllegalArgumentException {
			if (StringUtils.isEmpty(text)) {
				return null;
			}
			try {
				return parse0(text, zone.getRawOffset() / 3600000);
			} catch (ParseException e) {
				throw new IllegalArgumentException("Invalid date:" + text, e);
			}
		}

		/**
		 * 解析日期
		 * 
		 * @param text
		 * @param utcOffset 相对国际原子时的时差，从-12到+14(中国为8)
		 * @return
		 * @throws IllegalArgumentException
		 */
		public Date parse(String text, int utcOffset) throws IllegalArgumentException {
			if (StringUtils.isEmpty(text)) {
				return null;
			}
			try {
				return parse0(text, utcOffset);
			} catch (ParseException e) {
				throw new IllegalArgumentException("Invalid date:" + text, e);
			}
		}

		/**
		 * 解析时间，如果为空或者转换出错，都返回默认值
		 * 
		 * @param text
		 * @param defaultValue
		 * @return Date parsed.
		 */
		public Date parse(String text, Date defaultValue) {
			if (StringUtils.isEmpty(text)) {
				return defaultValue;
			}
			try {
				return get().parse(text);
			} catch (ParseException e) {
				return defaultValue;
			}
		}

		/**
		 * 解析时间，如果为空或者转换出错，都返回默认值
		 * 
		 * @param text
		 * @param defaultValue
		 * @return
		 */
		public Date parse(String text, Date defaultValue, TimeZone timeZone) {
			if (StringUtils.isEmpty(text)) {
				return defaultValue;
			}
			try {
				return parse0(text, timeZone.getRawOffset() / 3600000);
			} catch (ParseException e) {
				return defaultValue;
			}
		}

		private Date parse0(String text, int zoneOffet) throws ParseException {
			return get().get(zoneOffet + 12).parse(text);
		}
	}

	/**
	 * 支持全时区的DateFormat对象,共计27个UTC。包括 -12,0,+12 (东西十二区分为东十二区和西十二区)。 加上太平洋上的特殊时区
	 * 菲尼克斯群岛(UTC+13) 莱恩群岛(UTF+14)
	 */
	private static final class SuperDataFormat extends DateFormat {
		private static final long serialVersionUID = 5040737257964931800L;
		private final DateFormat[] f = new DateFormat[TIME_ZONES.length];
		private final int defaultUTC = (TimeZone.getDefault().getRawOffset() / 3600000 + 12);
		private SuperDataFormat(String pattern) {
			SimpleDateFormat DEFAULT = new SimpleDateFormat(pattern);
			for (String s : TIME_ZONES) {
				TimeZone z = TimeZone.getTimeZone(s);
				int offset = z.getRawOffset() / 3600000 + 12;
				f[offset] = (SimpleDateFormat) DEFAULT.clone();
				f[offset].setTimeZone(z);
			}
		}

		public DateFormat get(int offset) {
			if (offset >= 0 && offset <= 27) {
				return f[offset];
			} else {
				throw new IllegalArgumentException("Invalid TimeZone offset " + offset);
			}
		}

		@Override
		public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
			return f[defaultUTC].format(date, toAppendTo, fieldPosition);
		}

		@Override
		public Date parse(String source, ParsePosition pos) {
			return f[defaultUTC].parse(source, pos);
		}
	}

	/**
	 * 得到ThreadLocal对象的DateFormat
	 * 
	 * @param pattern
	 * @return
	 */
	public static final TLDateFormat getThreadLocalDateFormat(String pattern) {
		return new TLDateFormat(pattern);
	}

	public static void main(String[] args) {
		Date d=new Date();
		System.out.println(new TLDateFormat("yyyy-MM-dd'T'HH:mm:ss X").format(new Date(), 7));
	}
}
