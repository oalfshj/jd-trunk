package org.jdownloader.plugins.components;

//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.URLEncode;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptcha;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter.VideoExtensions;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.Form.MethodType;
import jd.parser.html.HTMLParser;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountRequiredException;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.SiteType.SiteTemplate;
import jd.plugins.hoster.RTMPDownload;

public class XFileSharingProBasic extends antiDDoSForHost {
    public XFileSharingProBasic(PluginWrapper wrapper) {
        super(wrapper);
        // this.enablePremium(super.getPurchasePremiumURL());
    }

    // public static List<String[]> getPluginDomains() {
    // final List<String[]> ret = new ArrayList<String[]>();
    // // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
    // ret.add(new String[] { "imgdew.com" });
    // return ret;
    // }
    //
    // public static String[] getAnnotationNames() {
    // return buildAnnotationNames(getPluginDomains());
    // }
    //
    // @Override
    // public String[] siteSupportedNames() {
    // return buildSupportedNames(getPluginDomains());
    // }
    //
    // public static String[] getAnnotationUrls() {
    // return XFileSharingProBasic.buildAnnotationUrls(getPluginDomains());
    // }
    // @Override
    // public String rewriteHost(String host) {
    // return this.rewriteHost(getPluginDomains(), host, new String[0]);
    // }
    public static final String getDefaultAnnotationPatternPart() {
        return "/(?:embed\\-)?[a-z0-9]{12}(?:/[^/]+(?:\\.html)?)?";
    }

    public static String[] buildAnnotationUrls(List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + XFileSharingProBasic.getDefaultAnnotationPatternPart());
        }
        return ret.toArray(new String[0]);
    }

    /* Used variables */
    public String                correctedBR                  = "";
    protected String             fuid                         = null;
    /*
     * Note:Final value will be set later in init(). CAN NOT be negative or zero! (ie. -1 or 0) Otherwise math sections fail. .:. use [1-20]
     */
    private static AtomicInteger totalMaxSimultanFreeDownload = new AtomicInteger(1);
    /* don't touch the following! */
    private static AtomicInteger maxFree                      = new AtomicInteger(1);

    /**
     * DEV NOTES XfileSharingProBasic Version 4.4.2.1<br />
     * mods: See overridden functions<br />
     * See official changelogs for upcoming XFS changes: https://sibsoft.net/xfilesharing/changelog.html |
     * https://sibsoft.net/xvideosharing/changelog.html <br/>
     * limit-info:<br />
     * captchatype-info: null 4dignum solvemedia reCaptchaV2<br />
     * Last compatible XFileSharingProBasic template: Version 2.7.8.7 in revision 40351 other:<br />
     */
    @Override
    public void init() {
        /* Errorhandling as we should not set negative values her!! */
        if (getMaxSimultaneousFreeAnonymousDownloads() < 0) {
            totalMaxSimultanFreeDownload.set(20);
        } else {
            totalMaxSimultanFreeDownload.set(getMaxSimultaneousFreeAnonymousDownloads());
        }
    }

    @Override
    public String getAGBLink() {
        return this.getMainPage() + "/tos.html";
    }

    public String getPurchasePremiumURL() {
        return this.getMainPage() + "/premium.html";
    }

    /**
     * Returns whether resume is supported or not for current download mode based on account availability and account type. <br />
     * Override this function to set resume settings!
     */
    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return true;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return false;
        }
    }

    /**
     * Returns how many max. chunks per file are allowed for current download mode based on account availability and account type. <br />
     * Override this function to set chunks settings!
     */
    public int getMaxChunks(final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return 1;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 1;
        }
    }

    // /** Returns max. simultaneous downloads for current download mode based on account availibility and account type. */
    // private int getDownloadModeMaxSimultaneousDownloads(final Account account) {
    // if (account.getType() == AccountType.FREE) {
    // /* Free Account */
    // return getMaxSimultaneousFreeAccountDownloads();
    // } else if (account.getType() == AccountType.PREMIUM) {
    // /* Premium account */
    // return getMaxSimultanPremiumDownloadNum();
    // } else {
    // /* Free(anonymous) and unknown account type */
    // return getMaxSimultaneousFreeAnonymousDownloads();
    // }
    // }
    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return maxFree.get();
    }

    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return 1;
    }

    public int getMaxSimultaneousFreeAccountDownloads() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    /** Returns direct-link-property-String for current download mode based on account availibility and account type. */
    protected static String getDownloadModeDirectlinkProperty(final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return "freelink2";
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return "premlink";
        } else {
            /* Free(anonymous) and unknown account type */
            return "freelink";
        }
    }

    /**
     * @return true: Website supports https and plugin will prefer https. <br />
     *         false: Website does not support https - plugin will avoid https. <br />
     *         default: true
     */
    protected boolean supports_https() {
        return true;
    }

    /**
     * Relevant for accounts.
     *
     * @return true: Try to find more exact (down to the second instead of day) expire date via '/?op=payments'. <br />
     *         false: For premium accounts: Do NOT try to find more exact expire date via '?op=payments'. Rely on given date string
     *         (yyyy-MM-dd) which is less precise. <br />
     *         default: true
     */
    protected boolean supports_precise_expire_date() {
        return true;
    }

    /**
     * <b> Enabling this leads to at least one additional http-request! </b>
     *
     * @return true: Implies that the hoster only allows audio-content to be uploaded. Enabling this will make plugin try to find
     *         audio-downloadlinks via '/mp3embed-<fuid>'. Also sets mime-hint via CompiledFiletypeFilter.ImageExtensions.JPG. <br />
     *         false: Website is just an usual filehost, use given fileextension if possible. <br />
     *         This is Deprecated since 2019-05-30 as we have not found a single XFS website which supported this for several yars now.
     *         default: false
     */
    @Deprecated
    protected boolean isAudiohoster() {
        return false;
    }

    /**
     * 2019-05-21: This old method is rarely supported in new XFS versions - you will usually not need this! <br />
     * <b> Enabling this will perform at least one additional http-request! </b> <br />
     * Enable this only for websites using <a href="https://sibsoft.net/xvideosharing.html">XVideosharing</a>. <br />
     * Demo-Website: <a href="http://xvideosharing.com">xvideosharing.com</a> <br />
     * Example-Host: <a href="http://clipsage.com">clipsage.com</a>
     *
     * @return true: Try to find final downloadlink via '/vidembed-<fuid>' request. <br />
     *         false: Skips this part. <br />
     *         default: false
     */
    @Deprecated
    protected boolean isVideohosterDirect() {
        return false;
    }

    /**
     * <b> Enabling this leads to at least one additional http-request! </b> <br />
     * Enable this for websites using <a href="https://sibsoft.net/xvideosharing.html">XVideosharing</a>. <br />
     * Demo-Website: <a href="http://xvideosharing.com">xvideosharing.com</a> DO NOT CALL THIS DIRECTLY - ALWAYS USE
     * internal_isVideohosterEmbed()!!!<br />
     *
     * @return true: Try to find final downloadlink via '/embed-<fuid>.html' request. <br />
     *         false: Skips this part. <br />
     *         default: false
     */
    protected boolean isVideohosterEmbed() {
        return false;
    }

    /**
     * Keep in mind: Most videohosters will allow embedding their videos thus a "video filename" should be enforced but they may also
     * sometimes NOT support embedding videos while a "video filename" should still be enforced - then this trigger might be useful! </br DO
     * NOT CALL THIS FUNCTION DIRECTLY! Use 'internal_isVideohoster_enforce_video_filename' instead!!
     *
     * @return true: Implies that the hoster only allows video-content to be uploaded. Enforces .mp4 extension for all URLs. Also sets
     *         mime-hint via CompiledFiletypeFilter.VideoExtensions.MP4. <br />
     *         false: Website is just a normal filehost and their filenames should contain the fileextension. <br />
     *         default: false
     */
    protected boolean isVideohoster_enforce_video_filename() {
        return false;
    }

    /**
     * Enable this for websites using <a href="https://sibsoft.net/ximagesharing.html">XImagesharing</a>. <br />
     * Demo-Website: <a href="http://ximagesharing.com">ximagesharing.com</a>
     *
     * @return true: Implies that the hoster only allows photo-content to be uploaded. Enabling this will make plugin try to find
     *         picture-downloadlinks. Also sets mime-hint via CompiledFiletypeFilter.ImageExtensions.JPG. <br />
     *         false: Website is just an usual filehost, use given fileextension if possible. <br />
     *         default: false
     */
    protected boolean isImagehoster() {
        return false;
    }

    /**
     * See also function getFilesizeViaAvailablecheckAlt! <br />
     * <b> Enabling this will eventually lead to at least one additional website-request! </b> <br/>
     * <b>DO NOT CALL THIS DIRECTLY, USE internal_supports_availablecheck_alt </b>
     *
     * @return true: Implies that website supports getFilesizeViaAvailablecheckAlt call as an alternative source for filesize-parsing.<br />
     *         false: Implies that website does NOT support getFilesizeViaAvailablecheckAlt. <br />
     *         default: true
     */
    protected boolean supports_availablecheck_alt() {
        return true;
    }

    /**
     * Only works when getFilesizeViaAvailablecheckAlt returns true! See getFilesizeViaAvailablecheckAlt!
     *
     * @return true: Implies that website supports getFilesizeViaAvailablecheckAlt call without Form-handling (one call less than usual) as
     *         an alternative source for filesize-parsing. <br />
     *         false: Implies that website does NOT support getFilesizeViaAvailablecheckAlt without Form-handling. <br />
     *         default: true
     */
    protected boolean supports_availablecheck_filesize_alt_fast() {
        return true;
    }

    /**
     * See also function getFilesizeViaAvailablecheckAlt!
     *
     * @return true: Website uses old version of getFilesizeViaAvailablecheckAlt. Old will be tried first, then new if it fails. <br />
     *         false: Website uses current version of getFilesizeViaAvailablecheckAlt - it will be used first and if it fails, old call will
     *         be tried. <br />
     *         2019-07-09: Do not override this anymore - this code will auto-detect this situation!<br/>
     *         default: false
     */
    @Deprecated
    protected boolean prefer_availablecheck_filesize_alt_type_old() {
        return false;
    }

    /**
     * See also function getFnameViaAbuseLink!<br />
     * <b> Enabling this will eventually lead to at least one additional website-request! </b> <br/>
     * DO NOT CALL THIS DIRECTLY - ALWAYS USE internal_supports_availablecheck_filename_abuse()!!!<br />
     *
     * @return true: Implies that website supports getFnameViaAbuseLink call as an alternative source for filename-parsing. <br />
     *         false: Implies that website does NOT support getFnameViaAbuseLink. <br />
     *         default: true
     */
    protected boolean supports_availablecheck_filename_abuse() {
        return true;
    }

    /**
     * @return true: Try to RegEx filesize from normal html code. If this fails due to static texts on a website or even fake information,
     *         all links of a filehost may just get displayed with the same/wrong filesize. <br />
     *         false: Do not RegEx filesize from normal html code. Plugin will still be able to find filesize if supports_availablecheck_alt
     *         or supports_availablecheck_alt_fast is enabled (=default)! <br />
     *         default: true
     */
    protected boolean supports_availablecheck_filesize_html() {
        return true;
    }

    /**
     * This is designed to find the filesize during availablecheck for videohosts - videohosts usually don't display the filesize anywhere!
     * <br />
     * CAUTION: Only set this to true if a filehost: <br />
     * 1. Allows users to embed videos via '/embed-<fuid>.html'. <br />
     * 2. Does not display a filesize anywhere inside html code or other calls where we do not have to do an http request on a directurl.
     * <br />
     * 3. Allows a lot of simultaneous connections. <br />
     * 4. Is FAST - if it is not fast, this will noticably slow down the linkchecking procedure! <br />
     * 5. Allows using a generated direct-URL at least two times.
     *
     * @return true: requestFileInformation will use '/embed' to do an additional offline-check and find the filesize. <br />
     *         false: Disable this.<br />
     *         default: false
     */
    protected boolean supports_availablecheck_filesize_via_embedded_video() {
        return false;
    }

    /**
     * A correct setting increases linkcheck-speed as unnecessary redirects will be avoided. <br />
     * Also in some cases, you may get 404 errors or redirects to other websites if this setting is not correct.
     *
     * @return true: Implies that website requires 'www.' in all URLs. <br />
     *         false: Implies that website does NOT require 'www.' in all URLs. <br />
     *         default: false
     */
    protected boolean requires_WWW() {
        return false;
    }

    /**
     * Implies that a host supports login via 'API Mod'[https://sibsoft.net/xfilesharing/mods/api.html] via one of these APIs:
     * https://xvideosharing.docs.apiary.io/ OR https://xfilesharingpro.docs.apiary.io/ <br />
     * This enabled = website relies on API - the complete XFS website can be used via API (very rare case!)</br>
     * Sadly, it seems like their linkcheck function only works on the files in the users' own account:
     * https://xvideosharing.docs.apiary.io/#reference/file/file-info/get-info/check-file(s) <br />
     * 2019-05-30: TODO: Add nice AccountFactory for hosts which have API support!<br />
     * 2019-08-20: Some XFS websites are supported via another API via play.google.com/store/apps/details?id=com.zeuscloudmanager --> This
     * has nothing todo with the official XFS API! </br>
     * Example: xvideosharing.com, flix555.com, uploadocean.com[2019-07-11: uploadocean API is broken] <br />
     * default: false
     */
    protected boolean supports_api_only_mode() {
        return false;
    }

    /**
     * 2019-08-20: Some websites' login will fail on the first attempt even with correct logindata. On the 2nd attempt a captcha will be
     * required and then the login should work. </br>
     * default = false
     */
    protected boolean allows_multiple_login_attempts_in_one_go() {
        return false;
    }

    /**
     * @return: Skip pre-download waittime or not. See waitTime function below. <br />
     *          default: false <br />
     *          example true: uploadrar.com
     */
    protected boolean preDownloadWaittimeSkippable() {
        return false;
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        final String fuid = this.fuid != null ? this.fuid : getFUIDFromURL(link);
        if (fuid != null) {
            /* link cleanup, prefer https if possible */
            if (link.getPluginPatternMatcher() != null && link.getPluginPatternMatcher().matches("https?://[A-Za-z0-9\\-\\.]+/embed-[a-z0-9]{12}")) {
                link.setContentUrl(getMainPage() + "/embed-" + fuid + ".html");
            }
            link.setPluginPatternMatcher(getMainPage() + "/" + fuid);
            link.setLinkID(getHost() + "://" + fuid);
        }
    }

    @Override
    public Browser prepBrowser(final Browser prepBr, final String host) {
        if (!(this.browserPrepped.containsKey(prepBr) && this.browserPrepped.get(prepBr) == Boolean.TRUE)) {
            super.prepBrowser(prepBr, host);
            /* define custom browser headers and language settings */
            prepBr.setCookie(getMainPage(), "lang", "english");
            prepBr.setAllowedResponseCodes(new int[] { 500 });
        }
        return prepBr;
    }

    /** Returns https?://host.tld */
    protected String getMainPage() {
        final String host;
        final String browser_host = this.br != null ? br.getHost() : null;
        final String[] hosts = this.siteSupportedNames();
        if (browser_host != null) {
            host = browser_host;
        } else {
            /* 2019-07-25: This may not be correct out of the box e.g. for imgmaze.com */
            host = hosts[0];
        }
        String mainpage;
        final String protocol;
        if (this.supports_https()) {
            protocol = "https://";
        } else {
            protocol = "http://";
        }
        mainpage = protocol;
        if (requires_WWW()) {
            mainpage += "www.";
        }
        mainpage += host;
        return mainpage;
    }

    protected final String getAPIBase() {
        return getMainPage() + "/api";
    }

    /**
     * @return true: Link is password protected <br />
     *         false: Link is not password protected
     */
    public boolean isPasswordProtectedHTM() {
        return new Regex(correctedBR, "<br><b>Passwor(d|t):</b> <input").matches();
    }

    /**
     * Checks premiumonly status via current Browser-URL + HTML.
     *
     * @return isPremiumOnlyURL || isPremiumOnlyHTML
     */
    public boolean isPremiumOnly() {
        final boolean isPremiumonlyURL = isPremiumOnlyURL();
        final boolean isPremiumonlyHTML = isPremiumOnlyHTML();
        return isPremiumonlyURL || isPremiumonlyHTML;
    }

    /**
     * Checks premiumonly status via current Browser-URL.
     *
     * @return true: Link only downloadable for premium users (sometimes also for registered users). <br />
     *         false: Link is downloadable for all users.
     */
    public boolean isPremiumOnlyURL() {
        return br.getURL() != null && br.getURL().contains("/?op=login&redirect=");
    }

    /**
     * Checks premiumonly status via current Browser-HTML.
     *
     * @return true: Link only downloadable for premium users (sometimes also for registered users). <br />
     *         false: Link is downloadable for all users.
     */
    public boolean isPremiumOnlyHTML() {
        final boolean premiumonly_filehost = new Regex(correctedBR, "( can download files up to |>\\s*Upgrade your account to download (?:larger|bigger) files|>\\s*The file you requested reached max downloads limit for Free Users|Please Buy Premium To download this file\\s*<|This file reached max downloads limit|>\\s*This file is available for Premium Users only|>\\s*Available Only for Premium Members|>File is available only for Premium users|>\\s*This file can be downloaded by)").matches();
        /* 2019-05-30: Example: xvideosharing.com */
        final boolean premiumonly_videohost = new Regex(correctedBR, ">\\s*This video is available for Premium users only").matches();
        return premiumonly_filehost || premiumonly_videohost;
    }

    /**
     * @return true: Website is in maintenance mode - downloads are not possible but linkcheck may be possible. <br />
     *         false: Website is not in maintenance mode and should usually work fine.
     */
    public boolean isWebsiteUnderMaintenance() {
        return br.getHttpConnection().getResponseCode() == 500 || new Regex(correctedBR, "\">\\s*This server is in maintenance mode").matches();
    }

    protected boolean isOffline(final DownloadLink link) {
        return br.getHttpConnection().getResponseCode() == 404 || new Regex(correctedBR, "(No such file|>\\s*File Not Found\\s*<|>\\s*The file was removed by|Reason for deletion:\n|File Not Found|>\\s*The file expired|>\\s*File could not be found due to expiration or removal by the file owner)").matches();
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformationWebsite(link, false);
    }

    public AvailableStatus requestFileInformationWebsite(final DownloadLink link, final boolean downloadsStarted) throws Exception {
        final String[] fileInfo = internal_getFileInfoArray();
        Browser altbr = null;
        fuid = null;
        correctDownloadLink(link);
        /* First, set fallback-filename */
        setWeakFilename(link);
        getPage(link.getPluginPatternMatcher());
        if (isOffline(link)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        setFUID(link);
        final String fallback_filename = this.getFallbackFilename(link);
        altbr = br.cloneBrowser();
        /*
         * 3 cases: 1. Host is currently under maintenance (VERY rare), 2. Host redirects to 'buy premium' URL --> We need to determine
         * filename AND filesize via alternative ways!, 3. Normal linkcheck (with fallbacks)
         */
        if (isWebsiteUnderMaintenance()) {
            /* VERY VERY rare case! */
            logger.info("Website is in maintenance mode");
            return AvailableStatus.UNCHECKABLE;
        } else if (isPremiumOnlyURL()) {
            /*
             * Hosts whose urls are all premiumonly usually don't display any information about the URL at all - only maybe online/ofline.
             * There are 2 alternative ways to get this information anyways!
             */
            logger.info("PREMIUMONLY linkcheck: Trying alternative linkcheck");
            /* Find filename */
            if (this.internal_supports_availablecheck_filename_abuse()) {
                fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
            }
            /* Find filesize */
            if (this.internal_supports_availablecheck_alt()) {
                getFilesizeViaAvailablecheckAlt(altbr, link);
            }
        } else {
            /* Normal handling */
            scanInfo(fileInfo);
            {
                /* Two possible reasons to use fallback handling to find filename! */
                /*
                 * Filename abbreviated over x chars long (common serverside XFS bug) --> Use getFnameViaAbuseLink as a workaround to find
                 * the full-length filename!
                 */
                if (!StringUtils.isEmpty(fileInfo[0]) && fileInfo[0].trim().endsWith("&#133;") && this.internal_supports_availablecheck_filename_abuse()) {
                    logger.warning("filename length is larrrge");
                    fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
                } else if (StringUtils.isEmpty(fileInfo[0]) && this.internal_supports_availablecheck_filename_abuse()) {
                    /* We failed to find the filename via html --> Try getFnameViaAbuseLink as workaround */
                    logger.info("Failed to find filename, trying getFnameViaAbuseLink");
                    fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
                }
            }
            /* Filesize fallback */
            if (StringUtils.isEmpty(fileInfo[1]) && this.internal_supports_availablecheck_alt()) {
                /* Failed to find filesize? Try alternative way! */
                getFilesizeViaAvailablecheckAlt(altbr, link);
            }
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = fallback_filename;
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            /* This should never happen! */
            logger.warning("filename equals null, throwing \"plugin defect\"");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        /* Set md5hash - most times there is no md5hash available! */
        if (!StringUtils.isEmpty(fileInfo[2])) {
            link.setMD5Hash(fileInfo[2].trim());
        }
        {
            /* Correct- and set filename */
            /*
             * Decode HtmlEntity encoding in filename if needed.
             */
            if (Encoding.isHtmlEntityCoded(fileInfo[0])) {
                fileInfo[0] = Encoding.htmlDecode(fileInfo[0]);
            }
            /* Remove some html tags - in most cases not necessary! */
            fileInfo[0] = fileInfo[0].replaceAll("(</b>|<b>|\\.html)", "").trim();
            if (this.internal_isVideohoster_enforce_video_filename()) {
                /* For videohosts we often get ugly filenames such as 'some_videotitle.avi.mkv.mp4' --> Correct that! */
                fileInfo[0] = this.removeDoubleExtensions(fileInfo[0], "mp4");
            }
            link.setName(fileInfo[0]);
        }
        {
            /* Set filesize */
            if (!StringUtils.isEmpty(fileInfo[1])) {
                link.setDownloadSize(SizeFormatter.getSize(fileInfo[1]));
            } else if (this.internal_isVideohosterEmbed() && supports_availablecheck_filesize_via_embedded_video() && !downloadsStarted) {
                /*
                 * Special case for some videohosts to determinethe filesize: Last chance to find filesize - do NOT execute this when used
                 * has started the download of our current DownloadLink as this could lead to "Too many connections" errors!
                 */
                requestFileInformationVideoEmbed(link, null, true);
            }
        }
        return AvailableStatus.TRUE;
    }

    protected final AvailableStatus requestFileInformationAPI(final DownloadLink link, final Account account) throws Exception {
        massLinkcheckerAPI(new DownloadLink[] { link }, account, false);
        return link.getAvailableStatus();
    }

    /**
     * 2019-05-15: This can check availability via '/embed' URL. <br />
     * Only call this if isVideohoster_2 is set to true.
     */
    protected void requestFileInformationVideoEmbed(final DownloadLink link, final Account account, final boolean findFilesize) throws Exception {
        /*
         * Some video sites contain their directurl right on the first page - let's use this as an indicator and assume that the file is
         * online if we find a directurl. This also speeds-up linkchecking! Example: uqload.com
         */
        String dllink = getDllink(link, account);
        if (StringUtils.isEmpty(dllink)) {
            if (br.getURL() != null && !br.getURL().contains("/embed")) {
                final String embed_access = getMainPage() + "/embed-" + fuid + ".html";
                getPage(embed_access);
                /**
                 * 2019-07-03: Example response when embedding is not possible (deactivated or it is not a video-file): "Can't create video
                 * code" OR "Video embed restricted for this user"
                 */
            }
            /*
             * Important: Do NOT use 404 as offline-indicator here as the website-owner could have simply disabled embedding while it was
             * enabled before --> This would return 404 for all '/embed' URLs! Only rely on precise errormessages!
             */
            // if (br.getHttpConnection().getResponseCode() == 404) {
            // throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            // }
            if (br.toString().equalsIgnoreCase("File was deleted")) {
                /* Should be valid for all XFS hosts e.g. speedvideo.net, uqload.com */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            // final String url_thumbnail = getVideoThumbnailURL(br.toString());
        }
        if (findFilesize) {
            if (StringUtils.isEmpty(dllink)) {
                dllink = getDllink(link, account);
            }
            /* Get- and set filesize from directurl */
            checkDirectLinkAndSetFilesize(link, dllink, true);
        }
    }

    /**
     * Tries to find filename, filesize and md5hash inside html. On Override, make sure to first use your special RegExes e.g.
     * fileInfo[0]="bla", THEN, if needed, call super.scanInfo(fileInfo). <br />
     * fileInfo[0] = filename, fileInfo[1] = filesize, fileInfo[2] = md5hash (rarely used, 2019-05-21: e.g. md5 hash available and special
     * case: filespace.com)
     */
    public String[] scanInfo(final String[] fileInfo) {
        /*
         * 2019-04-17: TODO: Improve sharebox RegExes (also check if we can remove/improve sharebox0 and sharebox1 RegExes) as this may save
         * us from having to use other time-comsuming fallbacks such as getFilesizeViaAvailablecheckAlt or getFnameViaAbuseLink. E.g. new
         * XFS often has good information in their shareboxes!
         */
        final String sharebox0 = "copy\\(this\\);.+>(.+) - ([\\d\\.]+ (?:B|KB|MB|GB))</a></textarea>[\r\n\t ]+</div>";
        final String sharebox1 = "copy\\(this\\);.+\\](.+) - ([\\d\\.]+ (?:B|KB|MB|GB))\\[/URL\\]";
        /* 2019-05-08: 'Forum Code': Sharebox with filename & filesize (bytes), example: snowfiles.com, brupload.net, qtyfiles.com */
        final String sharebox2 = "\\[URL=https?://(?:www\\.)?[^/\"]+/" + this.fuid + "[^\\]]*?\\]([^\"/]*?)\\s*\\-\\s*(\\d+)\\[/URL\\]";
        /* First found for pixroute.com URLs */
        final String sharebox2_without_filesize = "\\[URL=https?://(?:www\\.)?[^/\"]+/" + this.fuid + "/([^<>\"/\\]]*?)(?:\\.html)?\\]";
        /*
         * 2019-05-21: E.g. uqload.com, vidoba.net - this method will return a 'cleaner' filename than in other places - their titles will
         * often end with " mp4" which we have to correct later!
         */
        final String sharebox3_videohost = "\\[URL=https?://[^/]+/" + this.fuid + "[^/<>\\[\\]]*?\\]\\[IMG\\][^<>\"\\[\\]]+\\[/IMG\\]([^<>\"\\[\\]]+)\\[/URL\\]";
        /* standard traits from base page */
        if (StringUtils.isEmpty(fileInfo[0])) {
            /* 2019-06-12: TODO: Update this RegEx for e.g. up-4ever.org */
            fileInfo[0] = new Regex(correctedBR, "You have requested.*?https?://(?:www\\.)?[^/]+/" + fuid + "/(.*?)</font>").getMatch(0);
            if (StringUtils.isEmpty(fileInfo[0])) {
                /* 2019-05-21: E.g. datoporn.co */
                fileInfo[0] = new Regex(correctedBR, "fname\"( type=\"hidden\")? value=\"(.*?)\"").getMatch(1);
                if (StringUtils.isEmpty(fileInfo[0])) {
                    fileInfo[0] = new Regex(correctedBR, "<h2>Download File(.*?)</h2>").getMatch(0);
                    /* traits from download1 page below */
                    if (StringUtils.isEmpty(fileInfo[0])) {
                        fileInfo[0] = new Regex(correctedBR, "Filename:? ?(<[^>]+> ?)+?([^<>\"']+)").getMatch(1);
                    }
                }
            }
        }
        /* Next - RegExes for specified types of websites e.g. imagehosts */
        if (StringUtils.isEmpty(fileInfo[0]) && this.isImagehoster()) {
            fileInfo[0] = regexImagehosterFilename(correctedBR);
        }
        /* Next - details from sharing boxes (new RegExes to old) */
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, sharebox2).getMatch(0);
            if (StringUtils.isEmpty(fileInfo[0])) {
                fileInfo[0] = new Regex(correctedBR, sharebox2_without_filesize).getMatch(0);
            }
            if (StringUtils.isEmpty(fileInfo[0])) {
                fileInfo[0] = new Regex(correctedBR, sharebox1).getMatch(0);
                if (StringUtils.isEmpty(fileInfo[0])) {
                    fileInfo[0] = new Regex(correctedBR, sharebox0).getMatch(0);
                }
                if (StringUtils.isEmpty(fileInfo[0])) {
                    /* Link of the box without filesize */
                    fileInfo[0] = new Regex(correctedBR, "onFocus=\"copy\\(this\\);\">https?://(?:www\\.)?[^/]+/" + fuid + "/([^<>\"]*?)</textarea").getMatch(0);
                }
            }
        }
        /* Next - RegExes for videohosts */
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, sharebox3_videohost).getMatch(0);
            if (StringUtils.isEmpty(fileInfo[0])) {
                /* 2017-04-11: Typically for XVideoSharing sites */
                fileInfo[0] = new Regex(correctedBR, Pattern.compile("<title>Watch ([^<>\"]+)</title>", Pattern.CASE_INSENSITIVE)).getMatch(0);
            }
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, "class=\"dfilename\">([^<>\"]*?)<").getMatch(0);
        }
        /*
         * 2019-05-16: Experimental RegEx to find 'safe' filesize traits which can always be checked, regardless of the
         * 'supports_availablecheck_filesize_html' setting:
         */
        if (StringUtils.isEmpty(fileInfo[1])) {
            fileInfo[1] = new Regex(correctedBR, sharebox2).getMatch(1);
        }
        /* 2019-07-12: Example: Katfile.com */
        if (StringUtils.isEmpty(fileInfo[1])) {
            fileInfo[1] = new Regex(correctedBR, "id\\s*=\\s*\"fsize[^\"]*\"\\s*>\\s*([0-9\\.]+\\s*[MBTGK]+)\\s*<").getMatch(0);
        }
        if (StringUtils.isEmpty(fileInfo[1])) {
            /* 2019-07-12: Example: Katfile.com */
            fileInfo[1] = new Regex(correctedBR, "class\\s*=\\s*\"statd\"\\s*>\\s*size\\s*</span>\\s*<span>\\s*([0-9\\.]+\\s*[MBTGK]+)\\s*<").getMatch(0);
        }
        if (this.supports_availablecheck_filesize_html() && StringUtils.isEmpty(fileInfo[1])) {
            /** TODO: Clean this up */
            /* Starting from here - more unsafe attempts */
            if (StringUtils.isEmpty(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, "\\(([0-9]+ bytes)\\)").getMatch(0);
                if (StringUtils.isEmpty(fileInfo[1])) {
                    fileInfo[1] = getHighestVideoQualityFilesize();
                }
                if (StringUtils.isEmpty(fileInfo[1])) {
                    fileInfo[1] = new Regex(correctedBR, "</font>[ ]+\\(([^<>\"'/]+)\\)(.*?)</font>").getMatch(0);
                }
            }
            /* Next - unsafe details from sharing box */
            if (StringUtils.isEmpty(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, sharebox0).getMatch(1);
                if (StringUtils.isEmpty(fileInfo[1])) {
                    fileInfo[1] = new Regex(correctedBR, sharebox1).getMatch(1);
                }
            }
            /* Generic failover */
            if (StringUtils.isEmpty(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, "(\\d+(?:\\.\\d+)?(?: |\\&nbsp;)?(KB|MB|GB))").getMatch(0);
            }
        }
        /* MD5 is only available in very very rare cases! */
        if (StringUtils.isEmpty(fileInfo[2])) {
            fileInfo[2] = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
        }
        return fileInfo;
    }

    /**
     * Use this to Override 'checkLinks(final DownloadLink[])' in supported plugins. <br />
     * Used by getFilesizeViaAvailablecheckAlt <br />
     * <b>Use this only if:</b> <br />
     * - You have verified that the filehost has a mass-linkchecker and it is working fine with this code. <br />
     * - The contentURLs contain a filename as a fallback e.g. https://host.tld/<fuid>/someFilename.png.html </br>
     * - If used for single URLs inside 'normal linkcheck' (e.g. inside requestFileInformation), call with setWeakFilename = false <br/>
     * <b>- If used to check multiple URLs (mass-linkchecking feature), call with setWeakFilename = true!! </b>
     */
    public boolean massLinkcheckerWebsite(final DownloadLink[] urls, final boolean setWeakFilename) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        boolean linkcheckerHasFailed = false;
        String checkTypeCurrent = null;
        final String checkTypeOld = "checkfiles";
        final String checkTypeNew = "check_files";
        /* TODO: Use new settings for storing info here */
        final String checkType_last_used_and_working = getPluginConfig().getStringProperty("ALT_AVAILABLECHECK_LAST_WORKING", null);
        String checkURL = null;
        int linkcheckTypeTryCount = 0;
        try {
            final Browser br = new Browser();
            this.prepBrowser(br, getMainPage());
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            Form checkForm = null;
            while (true) {
                links.clear();
                while (true) {
                    /* we test 50 links at once */
                    if (index == urls.length || links.size() > 50) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                for (final DownloadLink dl : links) {
                    sb.append(URLEncode.encodeURIComponent(dl.getPluginPatternMatcher()));
                    sb.append("%0A");
                }
                {
                    /* Check if the mass-linkchecker works and which check we have to use */
                    while (linkcheckTypeTryCount <= 1) {
                        if (checkTypeCurrent != null) {
                            /* No matter which checkType we tried first - it failed and we need to try the other one! */
                            if (checkTypeCurrent.equals(checkTypeNew)) {
                                checkTypeCurrent = checkTypeOld;
                            } else {
                                checkTypeCurrent = checkTypeNew;
                            }
                        } else if (this.prefer_availablecheck_filesize_alt_type_old()) {
                            /* Old checkType forced? */
                            checkTypeCurrent = checkTypeOld;
                        } else if (checkType_last_used_and_working != null) {
                            /* Try to re-use last working method */
                            checkTypeCurrent = checkType_last_used_and_working;
                        } else {
                            /* First launch */
                            checkTypeCurrent = checkTypeNew;
                        }
                        checkURL = getMainPage() + "/?op=" + checkTypeCurrent;
                        /* TODO: Maybe add auto-detection for the requirement of supports_availablecheck_filesize_alt_fast */
                        /* Get and prepare Form */
                        if (this.supports_availablecheck_filesize_alt_fast()) {
                            /* Quick way - we do not access the page before and do not need to parse the Form. */
                            checkForm = new Form();
                            checkForm.setMethod(MethodType.POST);
                            checkForm.setAction(checkURL);
                            checkForm.put("op", checkTypeCurrent);
                            checkForm.put("process", "Check+URLs");
                        } else {
                            /* Try to get the Form IF NEEDED as it can contain tokens which would otherwise be missing. */
                            getPage(br, checkURL);
                            checkForm = br.getFormByInputFieldKeyValue("op", checkTypeCurrent);
                            if (checkForm == null) {
                                /* TODO: Add auto-retry so that 2nd type of linkchecker is either used directly or on the next attempt! */
                                logger.info("Failed to find Form for checkType: " + checkTypeCurrent);
                                linkcheckTypeTryCount++;
                                continue;
                            }
                        }
                        checkForm.put("list", sb.toString());
                        this.submitForm(br, checkForm);
                        /*
                         * Some hosts will not display any errorpage but also we will not be able to find any of our checked file-IDs inside
                         * the html --> Use this to find out about non-working linkchecking method!
                         */
                        final String example_fuid = this.getFUIDFromURL(links.get(0));
                        if (br.getHttpConnection().getResponseCode() == 404 || !br.getURL().contains(checkTypeCurrent) || !br.containsHTML(example_fuid)) {
                            /*
                             * This method of linkcheck is not supported - increase the counter by one to find out if ANY method worked in
                             * the end.
                             */
                            logger.info("Failed to find check_files Status via checkType: " + checkTypeCurrent);
                            linkcheckTypeTryCount++;
                            continue;
                        } else {
                            break;
                        }
                    }
                }
                for (final DownloadLink dl : links) {
                    final String fuid = this.getFUIDFromURL(dl);
                    String html_for_fuid = br.getRegex("<tr>((?!</?tr>).)*?" + fuid + "((?!</?tr>).)*?</tr>").getMatch(-1);
                    if (html_for_fuid == null) {
                        /*
                         * 2019-07-10: E.g. for old linkcheckers which only return online/offline status in a single line and not as a html
                         * table.
                         */
                        html_for_fuid = br.getRegex("<font color=\\'green\\'>[^>]*?" + fuid + "[^>]*?</font>").getMatch(-1);
                    }
                    if (html_for_fuid == null) {
                        logger.warning("Failed to find html_for_fuid --> Possible linkchecker failure");
                        linkcheckerHasFailed = true;
                        dl.setAvailableStatus(AvailableStatus.UNCHECKED);
                        return false;
                    }
                    if (new Regex(html_for_fuid, "Not found").matches()) {
                        dl.setAvailable(false);
                    } else {
                        /* We know that the file is online - let's try to find the filesize ... */
                        dl.setAvailable(true);
                        try {
                            final String[] tabla_data = new Regex(html_for_fuid, "<td>?(.*?)</td>").getColumn(0);
                            final String size = tabla_data[2];
                            if (size != null) {
                                /*
                                 * Filesize should definitly be given - but at this stage we are quite sure that the file is online so let's
                                 * not throw a fatal error if the filesize cannot be found.
                                 */
                                dl.setDownloadSize(SizeFormatter.getSize(size));
                            }
                        } catch (final Throwable e) {
                        }
                    }
                    if (setWeakFilename) {
                        /*
                         * We cannot get 'good' filenames via this call so we have to rely on our fallback-filenames (fuid or filename
                         * inside URL)!
                         */
                        setWeakFilename(dl);
                    }
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        } finally {
            if (linkcheckerHasFailed) {
                logger.info("Seems like checkfiles availablecheck is not supported by this host");
                this.getPluginConfig().setProperty("ALT_AVAILABLECHECK_LAST_FAILURE_TIMESTAMP", System.currentTimeMillis());
            } else {
                this.getPluginConfig().setProperty("ALT_AVAILABLECHECK_LAST_WORKING", checkTypeCurrent);
            }
        }
        return true;
    }

    /**
     * TODO: 2019-07-11: At the moment this cannot yet be called directly because of two reasons: 1. It does not have the auto-handling
     * implemented, see internal_supports_availablecheck_alt, 2. An account with apikey is required to make use of this! <br/>
     * <b> ONLY CALL THIS VIA getFilesizeViaAvailablecheckAlt until more XFS websites have full API support! </b>
     */
    public boolean massLinkcheckerAPI(final DownloadLink[] urls, final Account account, final boolean setWeakFilename) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        boolean linkcheckerHasFailed = false;
        try {
            final Browser br = new Browser();
            this.prepBrowser(br, getMainPage());
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                links.clear();
                while (true) {
                    /* we test 50 links at once */
                    if (index == urls.length || links.size() > 50) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                for (final DownloadLink dl : links) {
                    final String fuid = this.getFUIDFromURL(dl);
                    // sb.append("%0A");
                    sb.append(fuid);
                    sb.append("%2C");
                }
                getPage(br, getAPIBase() + "/file/info?key=" + this.getAPIKey(account) + "&file_code=" + this.fuid);
                LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
                final ArrayList<Object> ressourcelist = (ArrayList<Object>) entries.get("result");
                for (final DownloadLink link : links) {
                    boolean foundResult = false;
                    final String fuid = this.getFUIDFromURL(link);
                    for (final Object fileO : ressourcelist) {
                        entries = (LinkedHashMap<String, Object>) fileO;
                        final String fuid_temp = (String) entries.get("filecode");
                        if (fuid_temp != null && fuid_temp.equalsIgnoreCase(fuid)) {
                            foundResult = true;
                            break;
                        }
                    }
                    if (!foundResult) {
                        /*
                         * This should never happen. If it does, the apikey which was used by the user might not have access to this API
                         * call or can only check his own uploaded files!
                         */
                        linkcheckerHasFailed = true;
                        return false;
                    }
                    final long status = JavaScriptEngineFactory.toLong(entries.get("status"), 404);
                    if (status != 200) {
                        link.setAvailable(false);
                        setWeakFilename(link);
                    } else {
                        link.setAvailable(true);
                        String filename = (String) entries.get("name");
                        final long filesize = JavaScriptEngineFactory.toLong(entries.get("size"), 0);
                        final Object canplay = entries.get("canplay");
                        final Object views_started = entries.get("views_started");
                        final Object views = entries.get("views");
                        final Object length = entries.get("length");
                        final boolean isVideohost = canplay != null || views_started != null || views != null || length != null;
                        if (!StringUtils.isEmpty(filename)) {
                            /*
                             * TODO: Add check for fileextension! At least for videohosts, filenames from json do not contain a
                             * fileextension by default!
                             */
                            if (isVideohost) {
                                filename += ".mp4";
                            }
                            link.setFinalFileName(filename);
                        } else {
                            if (isVideohost) {
                                link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
                            }
                            if (setWeakFilename) {
                                setWeakFilename(link);
                            }
                        }
                        /* Filesize is not always given especially not for videohosts. */
                        if (filesize > 0) {
                            link.setDownloadSize(filesize);
                        }
                    }
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        } finally {
            if (linkcheckerHasFailed) {
                logger.info("Seems like massLinkcheckerAPI availablecheck is not supported by this host");
                this.getPluginConfig().setProperty("MASS_LINKCHECKER_API_LAST_FAILURE_TIMESTAMP", System.currentTimeMillis());
            }
        }
        return true;
    }

    /**
     * Try to find filename via '/?op=report_file&id=<fuid>'. Only call this function if internal_supports_availablecheck_filename_abuse()
     * returns true!<br />
     * E.g. needed if officially only logged in users can see filename or filename is missing in html code for whatever reason.<br />
     * Often needed for <b><u>IMAGEHOSTER</u> ' s</b>.<br />
     * Important: Only call this if <b><u>SUPPORTS_AVAILABLECHECK_ABUSE</u></b> is <b>true</b> (meaning omly try this if website supports
     * it)!<br />
     *
     * @throws Exception
     */
    public String getFnameViaAbuseLink(final Browser br, final DownloadLink dl, final String fallbackFilename) throws Exception {
        getPage(br, getMainPage() + "/?op=report_file&id=" + fuid, false);
        /*
         * 2019-07-10: ONLY "No such file" as response might always be wrong and should be treated as a failure! Example: xvideosharing.com
         */
        final boolean fnameViaAbuseUnsupported = br.getHttpConnection().getResponseCode() == 404 || br.getHttpConnection().getResponseCode() == 500 || !br.getURL().contains("report_file") || br.toString().trim().equals("No such file");
        if (br.containsHTML(">No such file<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = regexFilenameAbuse(br);
        if (filename == null) {
            logger.info("Failed to find filename via report_file - using fallbackFilename");
            filename = fallbackFilename;
            if (fnameViaAbuseUnsupported) {
                logger.info("Seems like report_file availablecheck seems not to be supported by this host");
                this.getPluginConfig().setProperty("REPORT_FILE_AVAILABLECHECK_LAST_FAILURE_TIMESTAMP", System.currentTimeMillis());
            }
        } else {
            logger.info("Successfully found filename via report_file");
        }
        return filename;
    }

    /** Part of getFnameViaAbuseLink(). */
    public String regexFilenameAbuse(final Browser br) {
        String filename = null;
        final String filename_src = br.getRegex("<b>Filename\\s*:?\\s*<[^\n]+</td>").getMatch(-1);
        if (filename_src != null) {
            filename = new Regex(filename_src, ">([^>]+)</td>$").getMatch(0);
        }
        return filename;
    }

    /** Only use this if it is made sure that the host we're working with is an imagehoster (ximagesharing)!! */
    public String regexImagehosterFilename(final String source) {
        return new Regex(source, "class=\"pic\" alt=\"([^<>\"]*?)\"").getMatch(0);
    }

    /**
     * Get filesize via massLinkchecker/alternative availablecheck.<br />
     * Often used as fallback if o.g. officially only logged-in users can see filesize or filesize is not given in html code for whatever
     * reason.<br />
     * Often needed for <b><u>IMAGEHOSTER</u> ' s</b>.<br />
     * Important: Only call this if <b><u>supports_availablecheck_alt</u></b> is <b>true</b> (meaning omly try this if website supports
     * it)!<br />
     * Some older XFS versions AND videohosts have versions of this linkchecker which only return online/offline and NO FILESIZE!</br>
     * In case there is no filesize given, offline status will still be recognized! <br/>
     *
     * @return isOnline
     */
    protected final boolean getFilesizeViaAvailablecheckAlt(final Browser br, final DownloadLink link) throws PluginException {
        logger.info("Trying getFilesizeViaAvailablecheckAlt");
        massLinkcheckerWebsite(new DownloadLink[] { link }, false);
        final boolean isChecked = link.isAvailabilityStatusChecked();
        if (isChecked) {
            logger.info("Successfully checked URL via massLinkchecker | filesize: " + link.getView().getBytesTotal());
        } else if (!link.isAvailable()) {
            logger.info("massLinkchecker detected offline URL");
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return isChecked;
    }

    /**
     * Removes double extensions (of video hosts) to correct ugly filenames such as 'some_videoname.mkv.flv.mp4'.<br />
     *
     * @param filename
     *            input filename whose extensions will be replaced by parameter defaultExtension.
     * @param desiredExtension
     *            Extension which is supposed to replace the (multiple) wrong extension(s). <br />
     *            If defaultExtension is null,this function will only remove existing extensions.
     */
    public String removeDoubleExtensions(String filename, final String desiredExtension) {
        if (filename == null || desiredExtension == null) {
            return filename;
        }
        /* First let's remove all [XVideosharing] common video extensions */
        final VideoExtensions[] videoExtensions = VideoExtensions.values();
        boolean foundExt = true;
        while (foundExt) {
            foundExt = false;
            /* Chek for video extensions at the end of the filename and remove them */
            for (final VideoExtensions videoExt : videoExtensions) {
                final Pattern pattern = videoExt.getPattern();
                final String extStr = pattern.toString();
                final Pattern removePattern = Pattern.compile(".+(( |\\.)" + extStr + ")$", Pattern.CASE_INSENSITIVE);
                final String removeThis = new Regex(filename, removePattern).getMatch(0);
                if (removeThis != null) {
                    filename = filename.replace(removeThis, "");
                    foundExt = true;
                    break;
                }
            }
        }
        /* Add desired video extension if given. */
        if (desiredExtension != null && !filename.endsWith("." + desiredExtension)) {
            filename += "." + desiredExtension;
        }
        return filename;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformationWebsite(downloadLink, true);
        doFree(downloadLink, null);
    }

    /** Handles pre-download forms & captcha for free (anonymous) + FREE ACCOUNT modes. */
    public void doFree(final DownloadLink link, final Account account) throws Exception, PluginException {
        /* First bring up saved final links */
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        /*
         * 2019-05-21: TODO: Maybe try download right away instead of checking this here --> This could speed-up the
         * download-start-procedure!
         */
        String dllink = checkDirectLink(link, directlinkproperty);
        /**
         * Try to find a downloadlink. Check different methods sorted from "usually available" to "rarely available" (e.g. there are a lot
         * of sites which support video embedding but nearly none support mp3-embedding).
         */
        /* Check for streaming/direct links on the first page. */
        if (StringUtils.isEmpty(dllink)) {
            checkErrors(link, account, false);
            dllink = getDllink(link, account);
        }
        /* Do they support standard video embedding? */
        if (StringUtils.isEmpty(dllink) && this.internal_isVideohosterEmbed()) {
            try {
                logger.info("Trying to get link via embed");
                requestFileInformationVideoEmbed(link, null, false);
                dllink = getDllink(link, account);
                if (StringUtils.isEmpty(dllink)) {
                    logger.info("FAILED to get link via embed");
                } else {
                    logger.info("Successfully found link via embed");
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via embed");
            }
            if (StringUtils.isEmpty(dllink)) {
                /* If failed, go back to the beginning */
                getPage(link.getPluginPatternMatcher());
            }
        }
        /* Do they provide direct video URLs? */
        if (StringUtils.isEmpty(dllink) && this.isVideohosterDirect()) {
            /* Legacy - most XFS videohosts do not support this anymore! */
            try {
                logger.info("Trying to get link via vidembed");
                final Browser brv = br.cloneBrowser();
                getPage(brv, "/vidembed-" + fuid, false);
                dllink = brv.getRedirectLocation();
                if (StringUtils.isEmpty(dllink)) {
                    logger.info("Failed to get link via vidembed because: " + br.toString());
                } else {
                    logger.info("Successfully found link via vidembed");
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via vidembed");
            }
        }
        /* Do they provide audio hosting? EXTREMELY rare case! */
        if (StringUtils.isEmpty(dllink) && link.getName().endsWith(".mp3") && this.isAudiohoster()) {
            try {
                logger.info("Trying to get link via mp3embed");
                final Browser brv = br.cloneBrowser();
                getPage(brv, "/mp3embed-" + fuid, false);
                dllink = brv.getRedirectLocation();
                if (StringUtils.isEmpty(dllink)) {
                    dllink = brv.getRegex("flashvars=\"file=(https?://[^<>\"]*?\\.mp3)\"").getMatch(0);
                }
                if (StringUtils.isEmpty(dllink)) {
                    logger.info("Failed to get link via mp3embed because: " + br.toString());
                } else {
                    logger.info("Successfully found link via mp3embed");
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via mp3embed");
            }
        }
        /* Do we have an imagehost? */
        if (StringUtils.isEmpty(dllink) && this.isImagehoster()) {
            checkErrors(link, account, false);
            Form imghost_next_form = null;
            do {
                imghost_next_form = findImageForm(this.br);
                if (imghost_next_form != null) {
                    /* end of backward compatibility */
                    submitForm(imghost_next_form);
                    checkErrors(link, account, false);
                    dllink = getDllink(link, account);
                    /* For imagehosts, filenames are often not given until we can actually see/download the image! */
                    final String image_filename = regexImagehosterFilename(correctedBR);
                    if (image_filename != null) {
                        link.setName(Encoding.htmlOnlyDecode(image_filename));
                    }
                }
            } while (imghost_next_form != null);
        }
        /* Continue like normal */
        if (StringUtils.isEmpty(dllink)) {
            /*
             * Check errors here because if we don't and a link is premiumonly, download1 Form will be present, plugin will send it and most
             * likely end up with error "Fatal countdown error (countdown skipped)"
             */
            checkErrors(link, account, false);
            final Form download1 = findFormDownload1();
            if (download1 != null) {
                /* end of backward compatibility */
                submitForm(download1);
                checkErrors(link, account, false);
                dllink = getDllink(link, account);
            }
        }
        if (StringUtils.isEmpty(dllink)) {
            dllink = checkOfficialVideoDownload(link, account);
        }
        if (StringUtils.isEmpty(dllink)) {
            Form dlForm = findFormF1();
            if (dlForm == null) {
                /* Last chance - maybe our errorhandling kicks in here. */
                checkErrors(link, account, false);
                /* Okay we finally have no idea what happened ... */
                logger.warning("Failed to find F1 dlForm via findFormF1");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            /* Define how many forms deep do we want to try? */
            int repeat = 2;
            for (int i = 0; i <= repeat; i++) {
                dlForm.remove(null);
                final long timeBefore = System.currentTimeMillis();
                if (isPasswordProtectedHTM()) {
                    logger.info("The downloadlink seems to be password protected.");
                    handlePassword(dlForm, link);
                }
                handleCaptcha(link, dlForm);
                /* 2019-02-08: MD5 can be on the subsequent pages - it is to be found very rare in current XFS versions */
                if (link.getMD5Hash() == null) {
                    final String md5hash = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
                    if (md5hash != null) {
                        link.setMD5Hash(md5hash.trim());
                    }
                }
                waitTime(link, timeBefore);
                final URLConnectionAdapter formCon = br.openFormConnection(dlForm);
                if (!formCon.getContentType().contains("text") && formCon.isOK() && formCon.getLongContentLength() > -1) {
                    /* Very rare case - e.g. tiny-files.com */
                    handleDownload(link, account, dllink, formCon.getRequest());
                    return;
                } else {
                    br.followConnection();
                    this.correctBR();
                    try {
                        formCon.disconnect();
                    } catch (final Throwable e) {
                    }
                }
                logger.info("Submitted DLForm");
                checkErrors(link, account, true);
                dllink = getDllink(link, account);
                final boolean dlformIsThere = findFormF1() != null;
                if (StringUtils.isEmpty(dllink) && (!dlformIsThere || i == repeat)) {
                    logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else if (StringUtils.isEmpty(dllink) && dlformIsThere) {
                    dlForm = findFormF1();
                    invalidateLastChallengeResponse();
                    continue;
                } else {
                    validateLastChallengeResponse();
                    break;
                }
            }
        }
        logger.info("Final downloadlink = " + dllink + " starting the download...");
        handleDownload(link, account, dllink, null);
    }

    /** Checks if official video download is possible and returns downloadlink if possible. */
    public String checkOfficialVideoDownload(final DownloadLink link, final Account account) throws Exception {
        String dllink = null;
        final String highestVideoQualityHTML = getHighestQualityHTML();
        if (highestVideoQualityHTML != null) {
            final Regex videoinfo = new Regex(highestVideoQualityHTML, "download_video\\(\\'([a-z0-9]+)\\',\\'([^<>\"\\']*?)\\',\\'([^<>\"\\']*?)\\'");
            // final String vid = videoinfo.getMatch(0);
            /* Usually this will be 'o' standing for "original quality" */
            final String q = videoinfo.getMatch(1);
            final String hash = videoinfo.getMatch(2);
            if (StringUtils.isEmpty(q) || StringUtils.isEmpty(hash)) {
                logger.warning("Failed to find required parameters");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            /* 2019-08-29: This may sometimes happen e.g. deltabit.co */
            this.waitTime(link, System.currentTimeMillis());
            final Browser brc = br.cloneBrowser();
            getPage(brc, "/dl?op=download_orig&id=" + this.fuid + "&mode=" + q + "&hash=" + hash);
            /* 2019-08-29: This Form may sometimes be given e.g. deltabit.co */
            final Form download1 = brc.getFormByInputFieldKeyValue("op", "download1");
            if (download1 != null) {
                this.submitForm(brc, download1);
                /*
                 * 2019-08-29: TODO: A 'checkErrors' is supposed to be here but at the moment not possible if we do not use our 'standard'
                 * browser
                 */
            }
            dllink = this.getDllink(link, account, brc, brc.toString());
            if (StringUtils.isEmpty(dllink)) {
                /* 2019-05-30: Test - worked for: xvideosharing.com */
                dllink = new Regex(brc.toString(), "<a href=\"(https?[^\"]+)\"[^>]*>Direct Download Link</a>").getMatch(0);
            }
            if (StringUtils.isEmpty(dllink)) {
                /* 2019-08-29: Test - worked for: deltabit.co */
                dllink = regexVideoStreamDownloadURL(brc.toString());
            }
            if (StringUtils.isEmpty(dllink)) {
                logger.info("Failed to find final downloadurl");
            }
        }
        return dllink;
    }

    protected void handleRecaptchaV2(final DownloadLink link, final Form captchaForm) throws Exception {
        /*
         * 2019-06-06: Most widespread case with an important design-flaw (see below)!
         */
        logger.info("Detected captcha method \"RecaptchaV2\" type 'normal' for this host");
        final CaptchaHelperHostPluginRecaptchaV2 rc2 = new CaptchaHelperHostPluginRecaptchaV2(this, br);
        if (!this.preDownloadWaittimeSkippable()) {
            final String waitStr = regexWaittime();
            if (waitStr != null && waitStr.matches("\\d+")) {
                final int waitSeconds = Integer.parseInt(waitStr);
                final int reCaptchaV2TimeoutSeconds = rc2.getSolutionTimeout();
                if (waitSeconds > reCaptchaV2TimeoutSeconds) {
                    /*
                     * Admins may sometimes setup waittimes that are higher than the reCaptchaV2 timeout so lets say they set up 180 seconds
                     * of pre-download-waittime --> User solves captcha immediately --> Captcha-solution times out after 120 seconds -->
                     * User has to re-enter it (and it would fail in JD)! If admins set it up in a way that users can solve the captcha via
                     * the waittime counts down, this failure may even happen via browser (example: xubster.com)! See workaround below!
                     */
                    /*
                     * This is basically a workaround which avoids running into reCaptchaV2 timeout: Make sure that we wait less than 120
                     * seconds after the user has solved the captcha. If the waittime is higher than 120 seconds, we'll wait two times:
                     * Before AND after the captcha!
                     */
                    final int prePreWait = waitSeconds % reCaptchaV2TimeoutSeconds;
                    logger.info("Waittime is higher than reCaptchaV2 timeout --> Waiting a part of it before solving captcha to avoid timeouts");
                    logger.info("Pre-pre download waittime seconds: " + prePreWait);
                    this.sleep(prePreWait * 1000l, link);
                }
            }
        }
        final String recaptchaV2Response = rc2.getToken();
        captchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
    }

    /** Handles all kinds of captchas, also login-captcha - fills the given captchaForm. */
    public void handleCaptcha(final DownloadLink link, final Form captchaForm) throws Exception {
        /* Captcha START */
        if (new Regex(correctedBR, Pattern.compile("\\$\\.post\\(\\s*\"/ddl\"", Pattern.CASE_INSENSITIVE)).matches()) {
            /* 2019-06-06: Rare case */
            logger.info("Detected captcha method \"RecaptchaV2\" type 'special' for this host");
            final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
            /*
             * 2017-12-07: New - solve- and check reCaptchaV2 here via ajax call, then wait- and submit the main downloadform. This might as
             * well be a workaround by the XFS developers to avoid expiring reCaptchaV2 challenges. Example: filefox.cc
             */
            /* 2017-12-07: New - this case can only happen during download and cannot be part of the login process! */
            /* Do not put the result in the given Form as the check itself is handled via Ajax right here! */
            captchaForm.put("g-recaptcha-response", "");
            final Form ajaxCaptchaForm = new Form();
            ajaxCaptchaForm.setMethod(MethodType.POST);
            ajaxCaptchaForm.setAction("/ddl");
            final InputField if_Rand = captchaForm.getInputFieldByName("rand");
            final String file_id = PluginJSonUtils.getJson(br, "file_id");
            if (if_Rand != null) {
                /* This is usually given */
                ajaxCaptchaForm.put("rand", if_Rand.getValue());
            }
            if (!StringUtils.isEmpty(file_id)) {
                /* This is usually given */
                ajaxCaptchaForm.put("file_id", file_id);
            }
            ajaxCaptchaForm.put("op", "captcha1");
            ajaxCaptchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
            /* User existing Browser object as we get a cookie which is required later. */
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            this.submitForm(br, ajaxCaptchaForm);
            if (!br.toString().equalsIgnoreCase("OK")) {
                logger.warning("Fatal reCaptchaV2 ajax handling failure");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.getHeaders().remove("X-Requested-With");
        } else if (StringUtils.containsIgnoreCase(correctedBR, "class=\"g-recaptcha\"") || StringUtils.containsIgnoreCase(correctedBR, "google.com/recaptcha")) {
            handleRecaptchaV2(link, captchaForm);
        } else {
            if (StringUtils.containsIgnoreCase(correctedBR, ";background:#ccc;text-align")) {
                logger.info("Detected captcha method \"plaintext captchas\" for this host");
                /* Captcha method by ManiacMansion */
                final String[][] letters = new Regex(br, "<span style='position:absolute;padding\\-left:(\\d+)px;padding\\-top:\\d+px;'>(&#\\d+;)</span>").getMatches();
                if (letters == null || letters.length == 0) {
                    logger.warning("plaintext captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final SortedMap<Integer, String> capMap = new TreeMap<Integer, String>();
                for (String[] letter : letters) {
                    capMap.put(Integer.parseInt(letter[0]), Encoding.htmlDecode(letter[1]));
                }
                final StringBuilder code = new StringBuilder();
                for (String value : capMap.values()) {
                    code.append(value);
                }
                captchaForm.put("code", code.toString());
                logger.info("Put captchacode " + code.toString() + " obtained by captcha metod \"plaintext captchas\" in the form.");
            } else if (StringUtils.containsIgnoreCase(correctedBR, "/captchas/")) {
                logger.info("Detected captcha method \"Standard captcha\" for this host");
                final String[] sitelinks = HTMLParser.getHttpLinks(br.toString(), "");
                String captchaurl = null;
                if (sitelinks == null || sitelinks.length == 0) {
                    logger.warning("Standard captcha captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                for (final String linkTmp : sitelinks) {
                    if (linkTmp.contains("/captchas/")) {
                        captchaurl = linkTmp;
                        break;
                    }
                }
                /*
                 * 2019-07-08: TODO: Strange - the following lines would be needed for subyshare.com as getHttpLinks does not pickup the URL
                 * we're looking for!
                 */
                // if (StringUtils.isEmpty(captchaurl)) {
                // captchaurl = new Regex(correctedBR, "(/captchas/[^<>\"\\']*)").getMatch(0);
                // }
                if (captchaurl == null) {
                    logger.warning("Standard captcha captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                String code = getCaptchaCode("xfilesharingprobasic", captchaurl, link);
                captchaForm.put("code", code);
                logger.info("Put captchacode " + code + " obtained by captcha metod \"Standard captcha\" in the form.");
            } else if (new Regex(correctedBR, "(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)").matches()) {
                logger.info("Detected captcha method \"reCaptchaV1\" for this host");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Website uses reCaptchaV1 which has been shut down by Google. Contact website owner!");
            } else if (new Regex(correctedBR, "solvemedia\\.com/papi/").matches()) {
                logger.info("Detected captcha method \"solvemedia\" for this host");
                final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia(br);
                File cf = null;
                try {
                    cf = sm.downloadCaptcha(getLocalCaptchaFile());
                } catch (final Exception e) {
                    if (org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia.FAIL_CAUSE_CKEY_MISSING.equals(e.getMessage())) {
                        throw new PluginException(LinkStatus.ERROR_FATAL, "Host side solvemedia.com captcha error - please contact the " + this.getHost() + " support");
                    }
                    throw e;
                }
                final String code = getCaptchaCode("solvemedia", cf, link);
                final String chid = sm.getChallenge(code);
                captchaForm.put("adcopy_challenge", chid);
                captchaForm.put("adcopy_response", "manual_challenge");
            } else if (br.containsHTML("id=\"capcode\" name= \"capcode\"")) {
                logger.info("Detected captcha method \"keycaptca\"");
                String result = handleCaptchaChallenge(getDownloadLink(), new KeyCaptcha(this, br, getDownloadLink()).createChallenge(this));
                if (result == null) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                if ("CANCEL".equals(result)) {
                    throw new PluginException(LinkStatus.ERROR_FATAL);
                }
                captchaForm.put("capcode", result);
            }
            /* Captcha END */
        }
    }

    /** Tries to find 1st download Form for free(and Free-Account) download. */
    public Form findFormDownload1() throws Exception {
        final Form download1 = br.getFormByInputFieldKeyValue("op", "download1");
        if (download1 != null) {
            download1.remove("method_premium");
            /* Fix/Add "method_free" value if necessary. */
            if (!download1.hasInputFieldByName("method_free") || download1.getInputFieldByName("method_free").getValue() == null) {
                String method_free_value = download1.getRegex("\"method_free\" value=\"([^<>\"]+)\"").getMatch(0);
                if (method_free_value == null || method_free_value.equals("")) {
                    method_free_value = "Free Download";
                }
                download1.put("method_free", Encoding.urlEncode(method_free_value));
            }
        }
        return download1;
    }

    /** Tries to find 2nd download Form for free(and Free-Account) download. */
    protected Form findFormF1() {
        Form dlForm = null;
        /* First try to find Form for video hosts with multiple qualities. */
        final Form[] forms = br.getForms();
        for (final Form form : forms) {
            final InputField op_field = form.getInputFieldByName("op");
            /* E.g. name="op" value="download_orig" */
            if (form.containsHTML("method_") && op_field != null && op_field.getValue().contains("download")) {
                dlForm = form;
                break;
            }
        }
        /* Nothing found? Fallback to simpler handling - this is more likely to pickup a wrong Form! */
        if (dlForm == null) {
            dlForm = br.getFormbyProperty("name", "F1");
        }
        if (dlForm == null) {
            dlForm = br.getFormByInputFieldKeyValue("op", "download2");
        }
        return dlForm;
    }

    /**
     * Tries to find download Form for premium download.
     *
     * @throws Exception
     */
    public Form findFormF1Premium() throws Exception {
        return br.getFormbyProperty("name", "F1");
    }

    /**
     * Checks if there are multiple video qualities available, finds html containing information of the highest video quality and returns
     * corresponding filesize if given.
     */
    protected final String getHighestQualityHTML() {
        final String[] videoQualities = new Regex(correctedBR, "download_video\\([^\r\t\n]+").getColumn(-1);
        long widthMax = 0;
        long widthTmp = 0;
        String targetHTML = null;
        for (final String videoQualityHTML : videoQualities) {
            final String filesizeTmpStr = regexFilesizeFromVideoDownloadHTML(videoQualityHTML);
            if (filesizeTmpStr != null) {
                widthTmp = SizeFormatter.getSize(filesizeTmpStr);
                if (widthTmp > widthMax) {
                    widthMax = widthTmp;
                    targetHTML = videoQualityHTML;
                }
            } else {
                /* This should not happen */
                logger.warning("Failed to find highest quality video download html --> Returning the first one");
                targetHTML = videoQualityHTML;
                break;
            }
        }
        return targetHTML;
    }

    /**
     * Returns filesize for highest video quality found via getHighestQualityHTML. <br />
     * This function is rarely used!
     */
    protected final String getHighestVideoQualityFilesize() {
        final String highestVideoQualityHTML = getHighestQualityHTML();
        return regexFilesizeFromVideoDownloadHTML(highestVideoQualityHTML);
    }

    private final String regexFilesizeFromVideoDownloadHTML(final String html) {
        return new Regex(html, "(([0-9\\.]+)\\s*(KB|MB|GB|TB))").getMatch(0);
    }

    /**
     * Check if a stored directlink exists under property 'property' and if so, check if it is still valid (leads to a downloadable content
     * [NOT html]).
     */
    protected final String checkDirectLink(final DownloadLink link, final String property) {
        final String dllink = link.getStringProperty(property);
        if (dllink != null) {
            final String ret = checkDirectLinkAndSetFilesize(link, dllink, false);
            if (ret != null) {
                return ret;
            } else {
                link.removeProperty(property);
                return null;
            }
        }
        return null;
    }

    /**
     * Checks if a directurl leads to downloadable content and if so, returns true. <br />
     * This will also return true if the serverside connection limit has been reached. <br />
     *
     * @param link
     *            : The DownloadLink
     * @param directurl
     *            : Directurl which should lead to downloadable content
     * @param setFilesize
     *            : true = setVerifiedFileSize filesize if directurl is really downloadable
     */
    protected final String checkDirectLinkAndSetFilesize(final DownloadLink link, final String directurl, final boolean setFilesize) {
        if (StringUtils.isEmpty(directurl) || !directurl.startsWith("http")) {
            return null;
        } else {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = openAntiDDoSRequestConnection(br2, br2.createHeadRequest(directurl));
                /* For video streams we often don't get a Content-Disposition header. */
                final boolean isFile = con.isContentDisposition() || StringUtils.containsIgnoreCase(con.getContentType(), "video") || StringUtils.containsIgnoreCase(con.getContentType(), "audio") || StringUtils.containsIgnoreCase(con.getContentType(), "application");
                if (con.getResponseCode() == 503) {
                    /* Ok */
                    /*
                     * Too many connections but that does not mean that our directlink is invalid. Accept it and if it still returns 503 on
                     * download-attempt this error will get displayed to the user - such directlinks should work again once there are less
                     * active connections to the host!
                     */
                    logger.info("directurl lead to 503 | too many connections");
                    return directurl;
                } else if (!con.getContentType().contains("html") && con.getLongContentLength() > -1 && con.isOK() && isFile) {
                    if (setFilesize) {
                        link.setVerifiedFileSize(con.getLongContentLength());
                    }
                    return directurl;
                } else {
                    /* Failure */
                }
            } catch (final Exception e) {
                /* Failure */
                logger.log(e);
            } finally {
                if (con != null) {
                    try {
                        con.disconnect();
                    } catch (final Throwable e) {
                    }
                }
            }
            return null;
        }
    }

    @Override
    public boolean hasAutoCaptcha() {
        return false;
    }

    @Override
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null || acc.getType() == AccountType.FREE) {
            /* Anonymous downloads & Free account downloads may have captchas */
            return true;
        } else {
            /* Premium accounts don't have captchas */
            return false;
        }
    }

    public ArrayList<String> getCleanupHTMLRegexes() {
        final ArrayList<String> regexStuff = new ArrayList<String>();
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        /* generic cleanup */
        regexStuff.add("<\\!(\\-\\-.*?\\-\\-)>");
        regexStuff.add("(display: ?none;\">.*?</div>)");
        regexStuff.add("(visibility:hidden>.*?<)");
        return regexStuff;
    }

    /* Removes HTML code which could break the plugin */
    protected void correctBR() throws NumberFormatException, PluginException {
        correctedBR = br.toString();
        final ArrayList<String> regexStuff = getCleanupHTMLRegexes();
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        /* generic cleanup */
        for (String aRegex : regexStuff) {
            String results[] = new Regex(correctedBR, aRegex).getColumn(0);
            if (results != null) {
                for (String result : results) {
                    correctedBR = correctedBR.replace(result, "");
                }
            }
        }
    }

    private final String getDllink(final DownloadLink link, final Account account) {
        return getDllink(link, account, this.br, correctedBR);
    }

    /** Function to find the final downloadlink. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected String getDllink(final DownloadLink link, final Account account, final Browser br, String src) {
        String dllink = br.getRedirectLocation();
        if (dllink == null || new Regex(dllink, this.getSupportedLinks()).matches()) {
            // dllink = new Regex(src, "(\"|')(" + String.format(dllinkRegexFile, getHostsPatternPart()) + ")\\1").getMatch(1);
            // /* Use wider and wider RegEx */
            // if (dllink == null) {
            // dllink = new Regex(src, "(" + String.format(dllinkRegexFile, getHostsPatternPart()) + ")(\"|')").getMatch(0);
            // }
            if (dllink == null) {
                /* Finally try without hardcoded domains */
                dllink = new Regex(src, "(" + String.format(getGenericDownloadlinkRegExFile(), "[A-Za-z0-9\\-\\.']+") + ")(\"|')(\\s+|\\s*>|\\s*\\)|\\s*;)").getMatch(0);
            }
            // if (dllink == null) {
            // /* Try short version */
            // dllink = new Regex(src, "(\"|')(" + String.format(dllinkRegexFile_2, getHostsPatternPart()) + ")\\1").getMatch(1);
            // }
            // if (dllink == null) {
            // /* Try short version without hardcoded domains and wide */
            // dllink = new Regex(src, "(" + String.format(dllinkRegexFile_2, getHostsPatternPart()) + ")").getMatch(0);
            // }
            /* 2019-02-02: TODO: Maybe add attempt to find downloadlink by the first url which ends with the filename */
            if (dllink == null) {
                final String cryptedScripts[] = new Regex(src, "p\\}\\((.*?)\\.split\\('\\|'\\)").getColumn(0);
                if (cryptedScripts != null && cryptedScripts.length != 0) {
                    for (String crypted : cryptedScripts) {
                        dllink = decodeDownloadLink(crypted);
                        if (dllink != null) {
                            break;
                        }
                    }
                }
            }
        }
        if (StringUtils.isEmpty(dllink)) {
            /* RegExes for videohosts */
            String jssource = new Regex(src, "sources\\s*:\\s*(\\[[^\\]]+\\])").getMatch(0);
            if (StringUtils.isEmpty(jssource)) {
                /* 2019-07-04: Wider attempt - find sources via pattern of their video-URLs. */
                jssource = new Regex(src, "[A-Za-z0-9]+\\s*:\\s*(\\[[^\\]]+[a-z0-9]{60}/v\\.mp4[^\\]]+\\])").getMatch(0);
            }
            if (!StringUtils.isEmpty(jssource)) {
                /*
                 * Different services store the values we want under different names. E.g. vidoza.net uses 'res', most providers use
                 * 'label'.
                 */
                final String[] possibleQualityObjectNames = new String[] { "label", "res" };
                /*
                 * Different services store the values we want under different names. E.g. vidoza.net uses 'src', most providers use 'file'.
                 */
                final String[] possibleStreamURLObjectNames = new String[] { "file", "src" };
                try {
                    HashMap<String, Object> entries = null;
                    Object quality_temp_o = null;
                    long quality_temp = 0;
                    long quality_best = 0;
                    String dllink_temp = null;
                    final ArrayList<Object> ressourcelist = (ArrayList) JavaScriptEngineFactory.jsonToJavaObject(jssource);
                    for (final Object videoo : ressourcelist) {
                        if (videoo instanceof String && ressourcelist.size() == 1) {
                            /* Maybe single URL without any quality information e.g. uqload.com */
                            dllink_temp = (String) videoo;
                            if (dllink_temp.startsWith("http")) {
                                dllink = dllink_temp;
                                break;
                            }
                        }
                        entries = (HashMap<String, Object>) videoo;
                        for (final String possibleStreamURLObjectName : possibleStreamURLObjectNames) {
                            if (entries.containsKey(possibleStreamURLObjectName)) {
                                dllink_temp = (String) entries.get(possibleStreamURLObjectName);
                                break;
                            }
                        }
                        for (final String possibleQualityObjectName : possibleQualityObjectNames) {
                            try {
                                quality_temp_o = entries.get(possibleQualityObjectName);
                                if (quality_temp_o != null && quality_temp_o instanceof Long) {
                                    quality_temp = JavaScriptEngineFactory.toLong(quality_temp_o, 0);
                                } else if (quality_temp_o != null && quality_temp_o instanceof String) {
                                    /* E.g. '360p' */
                                    quality_temp = Long.parseLong(new Regex((String) quality_temp_o, "(\\d+)p?$").getMatch(0));
                                }
                                if (quality_temp > 0) {
                                    break;
                                }
                            } catch (final Throwable e) {
                                continue;
                            }
                        }
                        if (StringUtils.isEmpty(dllink_temp) || quality_temp == 0) {
                            continue;
                        }
                        if (quality_temp > quality_best) {
                            quality_best = quality_temp;
                            dllink = dllink_temp;
                        }
                    }
                    if (!StringUtils.isEmpty(dllink)) {
                        logger.info("BEST handling for multiple video source succeeded");
                    }
                } catch (final Throwable e) {
                    logger.info("BEST handling for multiple video source failed");
                }
            }
            if (StringUtils.isEmpty(dllink)) {
                /* 2019-07-04: Examplehost: vidoza.net */
                dllink = regexVideoStreamDownloadURL(src);
            }
            if (StringUtils.isEmpty(dllink)) {
                final String check = new Regex(src, "file\\s*:\\s*\"(https?[^<>\"]*?\\.(?:mp4|flv))\"").getMatch(0);
                if (StringUtils.isNotEmpty(check) && !StringUtils.containsIgnoreCase(check, "/images/")) {
                    // jwplayer("flvplayer").onError(function()...
                    dllink = check;
                }
            }
        }
        if (dllink == null && this.isImagehoster()) {
            /* Used for imagehosts */
            /*
             * 2019-07-24: This is basically a small workaround because if a file has a "bad filename" the filename inside our URL may just
             * look like it is a thumbnail although it is not. If we find several URLs and all are the same we may still just take one of
             * them although it could be a thumbnail.
             */
            String lastDllink = null;
            boolean allResultsAreTheSame = true;
            final String[] possibleDllinks = new Regex(src, String.format(getGenericDownloadlinkRegExImage(), "[A-Za-z0-9\\-\\.']+")).getColumn(-1);
            for (final String possibleDllink : possibleDllinks) {
                if (possibleDllinks.length > 1 && lastDllink != null && !possibleDllink.equalsIgnoreCase(lastDllink)) {
                    allResultsAreTheSame = false;
                }
                /* Avoid downloading thumbnails */
                /* 2019-07-24: Improve recognization of thumbnails e.g. https://img67.imagetwist.com/th/123456/[a-z0-9]{12}.jpg */
                if (possibleDllink != null && !possibleDllink.matches(".+_t\\.[A-Za-z]{3,4}$")) {
                    dllink = possibleDllink;
                    break;
                }
                lastDllink = possibleDllink;
            }
            if (dllink == null && possibleDllinks.length > 1 && allResultsAreTheSame) {
                logger.info("image download-candidates were all identified as thumbnails --> Using first result anyways as it is likely that it is not a thumbnail!");
                dllink = possibleDllinks[0];
            }
        }
        return dllink;
    }

    private final String regexVideoStreamDownloadURL(final String src) {
        String dllink = new Regex(src, Pattern.compile("(https?://[^/]+[^\"]+[a-z0-9]{60}/v\\.mp4)", Pattern.CASE_INSENSITIVE)).getMatch(0);
        if (StringUtils.isEmpty(dllink)) {
            /* Wider attempt */
            dllink = new Regex(src, Pattern.compile("\"(https?://[^/]+/[a-z0-9]{60}/[^\"]+)\"", Pattern.CASE_INSENSITIVE)).getMatch(0);
        }
        return dllink;
    }

    /**
     * Returns URL to the video thumbnail. <br />
     * This might sometimes be useful when VIDEOHOSTER or VIDEOHOSTER_2 handling is used.
     */
    @Deprecated
    public String getVideoThumbnailURL(final String src) {
        String url_thumbnail = new Regex(src, "image\\s*:\\s*\"(https?://[^<>\"]+)\"").getMatch(0);
        if (StringUtils.isEmpty(url_thumbnail)) {
            /* 2019-05-16: e.g. uqload.com */
            url_thumbnail = new Regex(src, "poster\\s*:\\s*\"(https?://[^<>\"]+)\"").getMatch(0);
        }
        return url_thumbnail;
    }

    public String decodeDownloadLink(final String s) {
        String decoded = null;
        try {
            Regex params = new Regex(s, "'(.*?[^\\\\])',(\\d+),(\\d+),'(.*?)'");
            String p = params.getMatch(0).replaceAll("\\\\", "");
            int a = Integer.parseInt(params.getMatch(1));
            int c = Integer.parseInt(params.getMatch(2));
            String[] k = params.getMatch(3).split("\\|");
            while (c != 0) {
                c--;
                if (k[c].length() != 0) {
                    p = p.replaceAll("\\b" + Integer.toString(c, a) + "\\b", k[c]);
                }
            }
            decoded = p;
        } catch (Exception e) {
        }
        String finallink = null;
        if (decoded != null) {
            /* Open regex is possible because in the unpacked JS there are usually only 1-2 URLs. */
            finallink = new Regex(decoded, "(?:\"|')(https?://[^<>\"']*?\\.(avi|flv|mkv|mp4|m3u8))(?:\"|')").getMatch(0);
            if (finallink == null) {
                /* Maybe rtmp */
                finallink = new Regex(decoded, "(?:\"|')(rtmp://[^<>\"']*?mp4:[^<>\"']+)(?:\"|')").getMatch(0);
            }
        }
        return finallink;
    }

    public boolean isDllinkFile(final String url) {
        if (url == null) {
            return false;
        } else {
            return new Regex(url, Pattern.compile(String.format(getGenericDownloadlinkRegExFile(), "[A-Za-z0-9\\-\\.']+"), Pattern.CASE_INSENSITIVE)).matches();
        }
    }

    public boolean isDllinkImage(final String url) {
        if (url == null) {
            return false;
        } else {
            return new Regex(url, Pattern.compile(String.format(getGenericDownloadlinkRegExImage(), "[A-Za-z0-9\\-\\.']+"), Pattern.CASE_INSENSITIVE)).matches();
        }
    }

    /** Returns pre-download-waittime (seconds) from inside HTML. */
    protected String regexWaittime() {
        /**
         * TODO: 2019-05-15: Try to grab the whole line which contains "id"="countdown" and then grab the waittime from inside that as it
         * would probably make this more reliable.
         */
        /* Ticket Time */
        String waitStr = new Regex(correctedBR, "id=(?:\"|\\')countdown_str(?:\"|\\')[^>]*>[^<>]*<span id=[^>]*>\\s*(\\d+)\\s*</span>").getMatch(0);
        if (waitStr == null) {
            waitStr = new Regex(correctedBR, "class=\"seconds\"[^>]*?>\\s*(\\d+)\\s*</span>").getMatch(0);
        }
        if (waitStr == null) {
            /* More open RegEx */
            waitStr = new Regex(correctedBR, "class=\"seconds\">\\s*(\\d+)\\s*<").getMatch(0);
        }
        return waitStr;
    }

    public String getGenericDownloadlinkRegExFile() {
        return "https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|(?:[\\w\\-\\.]+\\.)?%s)(?::\\d{1,4})?/(?:files|d|cgi\\-bin/dl\\.cgi)/(?:\\d+/)?[a-z0-9]+/[^<>\"/]*?";
    }

    /*
     * Alternative / old:
     * https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|(?:[\\w\\-\\.]+\\.)?%s)(?::\\d{1,4})?/[a-z0-9]{50,}/[^<>\"/]*?
     */
    public String getGenericDownloadlinkRegExImage() {
        return "https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|(?:[\\w\\-\\.]+\\.)?%s)(?:/img/\\d+/[^<>\"'\\[\\]]+|/img/[a-z0-9]+/[^<>\"'\\[\\]]+|/img/[^<>\"'\\[\\]]+|/i/\\d+/[^<>\"'\\[\\]]+|/i/\\d+/[^<>\"'\\[\\]]+(?!_t\\.[A-Za-z]{3,4}))";
    }

    /**
     * Prevents more than one free download from starting at a given time. One step prior to dl.startDownload(), it adds a slot to maxFree
     * which allows the next singleton download to start, or at least try.
     *
     * This is needed because xfileshare(website) only throws errors after a final dllink starts transferring or at a given step within pre
     * download sequence. But this template(XfileSharingProBasic) allows multiple slots(when available) to commence the download sequence,
     * this.setstartintival does not resolve this issue. Which results in x(20) captcha events all at once and only allows one download to
     * start. This prevents wasting peoples time and effort on captcha solving and|or wasting captcha trading credits. Users will experience
     * minimal harm to downloading as slots are freed up soon as current download begins.
     *
     * @param num
     *            : (+1|-1)
     */
    protected synchronized void controlFree(final int num) {
        logger.info("maxFree was = " + maxFree.get());
        maxFree.set(Math.min(Math.max(1, maxFree.addAndGet(num)), totalMaxSimultanFreeDownload.get()));
        logger.info("maxFree now = " + maxFree.get());
    }

    @Override
    protected void getPage(String page) throws Exception {
        getPage(br, page, true);
    }

    protected void getPage(final Browser br, String page, final boolean correctBr) throws Exception {
        getPage(br, page);
        if (correctBr) {
            correctBR();
        }
    }

    @Override
    protected void postPage(String page, final String postdata) throws Exception {
        postPage(br, page, postdata, true);
    }

    protected void postPage(final Browser br, String page, final String postdata, final boolean correctBr) throws Exception {
        postPage(br, page, postdata);
        if (correctBr) {
            correctBR();
        }
    }

    @Override
    protected void submitForm(final Form form) throws Exception {
        submitForm(br, form, true);
    }

    protected void submitForm(final Browser br, final Form form, final boolean correctBr) throws Exception {
        submitForm(br, form);
        if (correctBr) {
            correctBR();
        }
    }

    /**
     * Handles pre download (pre-captcha) waittime.
     */
    protected void waitTime(final DownloadLink downloadLink, final long timeBefore) throws PluginException {
        /* Ticket Time */
        final String waitStr = regexWaittime();
        if (this.preDownloadWaittimeSkippable()) {
            /* Very rare case! */
            logger.info("Skipping pre-download waittime: " + waitStr);
        } else {
            final int extraWaitSeconds = 1;
            int wait;
            if (waitStr != null && waitStr.matches("\\d+")) {
                int passedTime = (int) ((System.currentTimeMillis() - timeBefore) / 1000) - extraWaitSeconds;
                logger.info("Found waittime, parsing waittime: " + waitStr);
                wait = Integer.parseInt(waitStr);
                /*
                 * Check how much time has passed during eventual captcha event before this function has been called and see how much time
                 * is left to wait.
                 */
                wait -= passedTime;
                if (passedTime > 0) {
                    /* This usually means that the user had to solve a captcha which cuts down the remaining time we have to wait. */
                    logger.info("Total passed time during captcha: " + passedTime);
                }
            } else {
                /* No waittime at all */
                wait = 0;
            }
            if (wait > 0) {
                logger.info("Waiting final waittime: " + wait);
                sleep(wait * 1000l, downloadLink);
            } else if (wait < -extraWaitSeconds) {
                /* User needed more time to solve the captcha so there is no waittime left :) */
                logger.info("Congratulations: Time to solve captcha was higher than waittime --> No waittime left");
            } else {
                /* No waittime at all */
                logger.info("Found no waittime");
            }
        }
    }

    /**
     * This fixes filenames from all xfs modules: file hoster, audio/video streaming (including transcoded video), or blocked link checking
     * which is based on fuid. 2019-06-12: TODO: Review this, check if this is still required for ANY XFS host!
     *
     * @version 0.4
     * @author raztoki
     */
    protected void fixFilename(final DownloadLink downloadLink) {
        String orgName = null;
        String orgExt = null;
        String servName = null;
        String servExt = null;
        String orgNameExt = downloadLink.getFinalFileName();
        if (orgNameExt == null) {
            orgNameExt = downloadLink.getName();
        }
        if (!StringUtils.isEmpty(orgNameExt) && orgNameExt.contains(".")) {
            orgExt = orgNameExt.substring(orgNameExt.lastIndexOf("."));
        }
        if (!StringUtils.isEmpty(orgExt)) {
            orgName = new Regex(orgNameExt, "(.+)" + Pattern.quote(orgExt)).getMatch(0);
        } else {
            orgName = orgNameExt;
        }
        // if (orgName.endsWith("...")) orgName = orgName.replaceFirst("\\.\\.\\.$", "");
        String servNameExt = dl.getConnection() != null && getFileNameFromHeader(dl.getConnection()) != null ? Encoding.htmlDecode(getFileNameFromHeader(dl.getConnection())) : null;
        if (!StringUtils.isEmpty(servNameExt) && servNameExt.contains(".")) {
            servExt = servNameExt.substring(servNameExt.lastIndexOf("."));
            servName = new Regex(servNameExt, "(.+)" + Pattern.quote(servExt)).getMatch(0);
        } else {
            servName = servNameExt;
        }
        String FFN = null;
        if (orgName.equalsIgnoreCase(fuid.toLowerCase())) {
            FFN = servNameExt;
        } else if (StringUtils.isEmpty(orgExt) && !StringUtils.isEmpty(servExt) && (servName.toLowerCase().contains(orgName.toLowerCase()) && !servName.equalsIgnoreCase(orgName))) {
            /*
             * when partial match of filename exists. eg cut off by quotation mark miss match, or orgNameExt has been abbreviated by hoster
             */
            FFN = servNameExt;
        } else if (!StringUtils.isEmpty(orgExt) && !StringUtils.isEmpty(servExt) && !orgExt.equalsIgnoreCase(servExt)) {
            FFN = orgName + servExt;
        } else {
            FFN = orgNameExt;
        }
        downloadLink.setFinalFileName(FFN);
    }

    /**
     * Sets XFS file-ID which is usually present inside the downloadurl added by the user. Usually it is [a-z0-9]{12}. <br />
     * Best to execute AFTER having accessed the downloadurl!
     */
    protected final void setFUID(final DownloadLink dl) throws PluginException {
        fuid = getFUIDFromURL(dl);
        /*
         * Rare case: Hoster has exotic URLs (e.g. migrated from other script e.g. YetiShare to XFS) --> Correct (internal) fuid is only
         * available via html
         */
        if (fuid == null) {
            /*
             * E.g. for hosts which migrate from other scripts such as YetiShare to XFS (example: hugesharing.net, up-4ever.org) and still
             * have their old URLs without XFS-fuid redirecting to the typical XFS URLs containing our fuid.
             */
            logger.info("fuid not given inside URL, trying to find it inside html");
            fuid = new Regex(correctedBR, "type=\"hidden\" name=\"id\" value=\"([a-z0-9]{12})\"").getMatch(0);
            if (fuid == null) {
                /* Last chance fallback */
                fuid = new Regex(br.getURL(), "https?://[^/]+/([a-z0-9]{12})").getMatch(0);
            }
            if (fuid == null) {
                /* fuid is crucial for us to have!! */
                logger.warning("Failed to find fuid inside html");
                /*
                 * 2019-06-12: Display such URLs as offline as this case is so rare that, if it happens, chances are very high that the file
                 * is offline anyways!
                 */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            logger.info("Found fuid inside html: " + fuid);
            correctDownloadLink(dl);
        }
    }

    /** Returns unique id from inside URL - usually with this pattern: [a-z0-9]{12} */
    public String getFUIDFromURL(final DownloadLink dl) {
        try {
            final String result = new Regex(new URL(dl.getPluginPatternMatcher()).getPath(), "/(?:embed-)?([a-z0-9]{12})").getMatch(0);
            return result;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * In some cases, URL may contain filename which can be used as fallback e.g. 'https://host.tld/<fuid>/<filename>(\\.html)?'. </br>
     * Examples without '.html' ending: vipfile.cc, prefiles.com
     */
    public String getFilenameFromURL(final DownloadLink dl) {
        try {
            String result = null;
            final String url_name_RegEx = "[a-z0-9]{12}/(.*?)(?:\\.html)?$";
            if (dl.getContentUrl() != null) {
                result = new Regex(new URL(dl.getContentUrl()).getPath(), url_name_RegEx).getMatch(0);
            }
            if (result == null) {
                result = new Regex(new URL(dl.getPluginPatternMatcher()).getPath(), url_name_RegEx).getMatch(0);
            }
            return result;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Tries to get filename from URL and if this fails, will return <fuid> filename. <br/>
     * Execute setFUID() BEFORE YOU EXECUTE THIS OR THE PLUGIN MAY FAIL TO FIND A (fallback-) FILENAME! In very rare cases (e.g. XFS owner
     * migrated to XFS from other script) this is important! See description of setFUID for more information!
     */
    public String getFallbackFilename(final DownloadLink dl) {
        String fallback_filename = this.getFilenameFromURL(dl);
        if (fallback_filename == null) {
            fallback_filename = this.getFUIDFromURL(dl);
        }
        return fallback_filename;
    }

    public void handlePassword(final Form pwform, final DownloadLink thelink) throws PluginException {
        String passCode = thelink.getDownloadPassword();
        if (passCode == null) {
            passCode = Plugin.getUserInput("Password?", thelink);
            if (StringUtils.isEmpty(passCode)) {
                logger.info("User has entered blank password, exiting handlePassword");
                thelink.setDownloadPassword(null);
                throw new PluginException(LinkStatus.ERROR_FATAL, "Pre-Download Password not provided");
            }
        }
        logger.info("Put password \"" + passCode + "\" entered by user in the DLForm.");
        pwform.put("password", Encoding.urlEncode(passCode));
        thelink.setDownloadPassword(passCode);
        return;
    }

    /**
     * Checks for (-& handles) all kinds of errors e.g. wrong captcha, wrong downloadpassword, waittimes and server error-responsecodes such
     * as 403, 404 and 503. <br />
     * checkAll: If enabled, ,this will also check for wrong password, wrong captcha and 'Skipped countdown' errors. <br/>
     * TODO: If account != null: Consider setting account traffic to 0 on reached downloadlink
     */
    protected void checkErrors(final DownloadLink link, final Account account, final boolean checkAll) throws NumberFormatException, PluginException {
        if (checkAll) {
            if (isPasswordProtectedHTM() && correctedBR.contains("Wrong password")) {
                final String userEnteredPassword = link.getDownloadPassword();
                /* handle password has failed in the past, additional try catching / resetting values */
                logger.warning("Wrong password, the entered password \"" + userEnteredPassword + "\" is wrong, retrying...");
                link.setDownloadPassword(null);
                throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
            }
            if (correctedBR.contains("Wrong captcha")) {
                logger.warning("Wrong captcha or wrong password!");
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            if (new Regex(correctedBR, ">\\s*Skipped countdown\\s*<").matches()) {
                /* 2019-08-28: e.g. "<br><b class="err">Skipped countdown</b><br>" */
                throw new PluginException(LinkStatus.ERROR_FATAL, "Fatal countdown error (countdown skipped)");
            }
        }
        /** Wait time reconnect handling */
        final String limitBasedOnNumberofFilesAndTime = new Regex(correctedBR, ">(You have reached the maximum limit \\d+ files in \\d+ hours)").getMatch(0);
        final String preciseWaittime = new Regex(correctedBR, "((You have reached the download(\\-| )limit|You have to wait)[^<>]+)").getMatch(0);
        if (preciseWaittime != null) {
            /* Reconnect waittime with given (exact) waittime usually either up to the minute or up to the second. */
            final String tmphrs = new Regex(preciseWaittime, "\\s*(\\d+)\\s*hours?").getMatch(0);
            final String tmpmin = new Regex(preciseWaittime, "\\s*(\\d+)\\s*minutes?").getMatch(0);
            final String tmpsec = new Regex(preciseWaittime, "\\s*(\\d+)\\s*seconds?").getMatch(0);
            final String tmpdays = new Regex(preciseWaittime, "\\s*(\\d+)\\s*days?").getMatch(0);
            int waittime;
            if (tmphrs == null && tmpmin == null && tmpsec == null && tmpdays == null) {
                /* This should not happen! This is an indicator of developer-failure! */
                logger.info("Waittime RegExes seem to be broken - using default waittime");
                waittime = 60 * 60 * 1000;
            } else {
                int minutes = 0, seconds = 0, hours = 0, days = 0;
                if (tmphrs != null) {
                    hours = Integer.parseInt(tmphrs);
                }
                if (tmpmin != null) {
                    minutes = Integer.parseInt(tmpmin);
                }
                if (tmpsec != null) {
                    seconds = Integer.parseInt(tmpsec);
                }
                if (tmpdays != null) {
                    days = Integer.parseInt(tmpdays);
                }
                waittime = ((days * 24 * 3600) + (3600 * hours) + (60 * minutes) + seconds + 1) * 1000;
            }
            logger.info("Detected reconnect waittime (milliseconds): " + waittime);
            /* Not enough wait time to reconnect -> Wait short and retry */
            if (waittime < 180000) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait until new downloads can be started", waittime);
            } else if (account != null) {
                throw new AccountUnavailableException("Download limit reached", waittime);
            } else {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, waittime);
            }
        } else if (limitBasedOnNumberofFilesAndTime != null) {
            /*
             * 2019-05-09: New: Seems like XFS owners can even limit by number of files inside specified timeframe. Example: hotlink.cc; 150
             * files per 24 hours
             */
            /* Typically '>You have reached the maximum limit 150 files in 24 hours' */
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, limitBasedOnNumberofFilesAndTime);
        } else if (correctedBR.contains("You're using all download slots for IP")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Server error 'You're using all download slots for IP'", 10 * 60 * 1001l);
        } else if (correctedBR.contains("Error happened when generating Download Link")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Error happened when generating Download Link'", 10 * 60 * 1000l);
        }
        /** Error handling for premiumonly links */
        if (isPremiumOnlyHTML()) {
            String filesizelimit = new Regex(correctedBR, "You can download files up to(.*?)only").getMatch(0);
            if (filesizelimit != null) {
                filesizelimit = filesizelimit.trim();
                throw new AccountRequiredException("As free user you can download files up to " + filesizelimit + " only");
            } else {
                logger.info("Only downloadable via premium");
                throw new AccountRequiredException();
            }
        } else if (isPremiumOnlyURL()) {
            logger.info("Only downloadable via premium");
            throw new AccountRequiredException();
        } else if (correctedBR.contains(">Expired download session")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Expired download session'", 10 * 60 * 1000l);
        }
        if (isWebsiteUnderMaintenance()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is under maintenance", 2 * 60 * 60 * 1000l);
        }
        /* Host-type specific errors */
        /* Videohoster */
        if (correctedBR.contains(">\\s*Video is processing now")) {
            /* E.g. '<div id="over_player_msg">Video is processing now. <br>Conversion stage: <span id='enc_pp'>...</span></div>' */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Not (yet) downloadable: Video is still being encoded or broken", 10 * 60 * 1000l);
        }
        checkResponseCodeErrors(br.getHttpConnection());
    }

    /** Handles all kinds of error-responsecodes! */
    public void checkResponseCodeErrors(final URLConnectionAdapter con) throws PluginException {
        if (con == null) {
            return;
        }
        final long responsecode = con.getResponseCode();
        if (responsecode == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 5 * 60 * 1000l);
        } else if (responsecode == 404) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 5 * 60 * 1000l);
        } else if (responsecode == 416) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 416", 5 * 60 * 1000l);
        } else if (responsecode == 500) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 500", 5 * 60 * 1000l);
        } else if (responsecode == 503) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server error 503 connection limit reached", 5 * 60 * 1000l);
        }
    }

    /**
     * Handles all kinds of errors which can happen if we get the final downloadlink but we get html code instead of the file we want to
     * download.
     */
    public void checkServerErrors() throws NumberFormatException, PluginException {
        if (new Regex(correctedBR.trim(), "^No file$").matches()) {
            /* Possibly dead file but it is supposed to be online so let's wait and retry! */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "^Wrong IP$").matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: 'Wrong IP'", 2 * 60 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "^Expired$").matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: 'Expired'", 2 * 60 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "(^File Not Found$|<h1>404 Not Found</h1>)").matches()) {
            /* most likely result of generated link that has expired -raztoki */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        }
    }

    protected boolean supports_lifetime_account() {
        return false;
    }

    protected boolean is_lifetime_account() {
        return new Regex(correctedBR, ">Premium account expire</TD><TD><b>Lifetime</b>").matches();
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai;
        if (this.supports_api_only_mode()) {
            ai = this.fetchAccountInfoAPI(this.br, account, true);
        } else {
            ai = this.fetchAccountInfoWebsite(account);
        }
        return ai;
    }

    protected AccountInfo fetchAccountInfoWebsite(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        loginWebsite(account, true);
        final String account_info_url_relative = getRelativeAccountInfoURL();
        /*
         * Only access URL if we haven't accessed it before already. Some sites will redirect to their Account-Info page right after
         * logging-in or our login-function when it is verifying cookies and not performing a full login.
         */
        if (br.getURL() == null || !br.getURL().contains(account_info_url_relative)) {
            getPage(this.getMainPage() + account_info_url_relative);
        }
        boolean api_success = false;
        {
            /*
             * 2019-07-11: apikey handling - prefer that instead of website if allowed.
             */
            String apikey = null;
            try {
                /*
                 * 2019-08-13: Do not hand over corrected_br as source as correctBR() might remove important parts of the html and because
                 * XFS owners will usually not add html traps into the html of accounts we can use the original unmodified html here.
                 */
                apikey = this.findAPIKey(br.toString());
            } catch (final Throwable e) {
                /*
                 * 2019-08-16: All kinds of errors may happen when trying to access the API. It is preferable if it works but we cannot rely
                 * on it working so we need that website fallback!
                 */
                logger.info("Failed to find apikey (with Exception) --> Continuing via website");
                e.printStackTrace();
            }
            if (apikey != null) {
                /*
                 * 2019-07-11: Use API even if 'supports_api()' is disabled because if it works it is a much quicker and more reliable way
                 * to get account information.
                 */
                logger.info("Found apikey --> Trying to get accountinfo via API");
                /* Save apikey for possible future usage */
                account.setProperty("apikey", apikey);
                try {
                    ai = this.fetchAccountInfoAPI(this.br.cloneBrowser(), account, false);
                    api_success = true;
                } catch (final Throwable e) {
                    e.printStackTrace();
                    logger.warning("Failed to find accountinfo via API even though apikey is given; probably serverside API failure --> Falling back to website handling");
                }
            }
        }
        /* 2019-08-21: Available traffic is never given via API so we'll have to check for it via website. */
        String trafficLeftStr = regExTrafficLeft();
        /* Example non english: brupload.net */
        final boolean userHasUnlimitedTraffic = trafficLeftStr != null && trafficLeftStr.matches(".*?nlimited|Ilimitado.*?");
        if (trafficLeftStr != null && !userHasUnlimitedTraffic && !trafficLeftStr.equalsIgnoreCase("Mb")) {
            trafficLeftStr = Encoding.htmlDecode(trafficLeftStr);
            trafficLeftStr.trim();
            /* need to set 0 traffic left, as getSize returns positive result, even when negative value supplied. */
            long trafficLeft = 0;
            if (trafficLeftStr.startsWith("-")) {
                /* Negative traffic value = User downloaded more than he is allowed to (rare case) --> No traffic left */
                trafficLeft = 0;
            } else {
                trafficLeft = (SizeFormatter.getSize(trafficLeftStr));
            }
            /* 2019-02-19: Users can buy additional traffic packages: Example(s): subyshare.com */
            final String usableBandwidth = br.getRegex("Usable Bandwidth\\s*<span.*?>\\s*([0-9\\.]+\\s*[TGMKB]+)\\s*/\\s*[0-9\\.]+\\s*[TGMKB]+\\s*<").getMatch(0);
            if (usableBandwidth != null) {
                trafficLeft = Math.max(trafficLeft, SizeFormatter.getSize(usableBandwidth));
            }
            ai.setTrafficLeft(trafficLeft);
        } else {
            ai.setUnlimitedTraffic();
        }
        if (api_success) {
            logger.info("Successfully found AccountInfo via API");
            return ai;
        }
        final String space[] = new Regex(correctedBR, ">Used space:</td>.*?<td.*?b>([0-9\\.]+) ?(KB|MB|GB|TB)?</b>").getRow(0);
        if ((space != null && space.length != 0) && (space[0] != null && space[1] != null)) {
            /* free users it's provided by default */
            ai.setUsedSpace(space[0] + " " + space[1]);
        } else if ((space != null && space.length != 0) && space[0] != null) {
            /* premium users the Mb value isn't provided for some reason... */
            ai.setUsedSpace(space[0] + "Mb");
        }
        if (supports_lifetime_account() && is_lifetime_account()) {
            ai.setValidUntil(-1);
            setAccountLimitsByType(account, AccountType.LIFETIME);
        } else {
            /* 2019-07-11: It is not uncommon for XFS websites to display expire-dates even though the account is not premium anymore! */
            String expireStr = new Regex(correctedBR, "(\\d{1,2} (January|February|March|April|May|June|July|August|September|October|November|December) \\d{4})").getMatch(0);
            long expire_milliseconds = 0;
            long expire_milliseconds_from_expiredate = 0;
            long expire_milliseconds_precise_to_the_second = 0;
            if (expireStr != null) {
                /*
                 * 2019-07-10: Accounts should expire at the end of the last day (verified via API of XFS demo website xvideosharing.com
                 * though for flix555.com is was different)
                 */
                expireStr += " 23:59:59";
                expire_milliseconds_from_expiredate = TimeFormatter.getMilliSeconds(expireStr, "dd MMMM yyyy HH:mm:ss", Locale.ENGLISH);
            }
            final boolean supports_precise_expire_date = this.supports_precise_expire_date();
            long currentTime = ai.getCurrentServerTime(br, System.currentTimeMillis());
            if (supports_precise_expire_date) {
                /*
                 * A more accurate expire time, down to the second. Usually shown on 'extend premium account' page. Case[0] e.g.
                 * 'flashbit.cc', Case [1] e.g. takefile.link, example website which has no precise expiredate at all: anzfile.net
                 */
                final String[] paymentURLs = new String[] { "/?op=payments", "/upgrade" };
                for (final String paymentURL : paymentURLs) {
                    try {
                        getPage(paymentURL);
                    } catch (final Throwable e) {
                        /* Skip failures due to timeout or bad http error-responses */
                        continue;
                    }
                    /* We want to be exact - get current server time for every loop! */
                    currentTime = ai.getCurrentServerTime(br, System.currentTimeMillis());
                    final String preciseExpireHTML = new Regex(correctedBR, "<div class=\"accexpire\"[^>]+>.*?</div>").getMatch(-1);
                    String expireSecond = new Regex(preciseExpireHTML, "Premium(-| )Account expires?\\s*:\\s*(?:</span>)?\\s*(?:<span>)?\\s*([a-zA-Z0-9, ]+)\\s*</").getMatch(-1);
                    if (StringUtils.isEmpty(expireSecond)) {
                        /*
                         * Last attempt - wider RegEx but we expect the 'second(s)' value to always be present!! Example: file-up.org:
                         * "<p style="direction: ltr; display: inline-block;">1 year, 352 days, 22 hours, 36 minutes, 45 seconds</p>"
                         */
                        expireSecond = new Regex(preciseExpireHTML, Pattern.compile(">\\s*(\\d+ years?, )?(\\d+ days?, )?(\\d+ hours?, )?(\\d+ minutes?, )?\\d+ seconds\\s*<", Pattern.CASE_INSENSITIVE)).getMatch(-1);
                    }
                    if (!StringUtils.isEmpty(expireSecond)) {
                        String tmpYears = new Regex(expireSecond, "(\\d+)\\s+years?").getMatch(0);
                        String tmpdays = new Regex(expireSecond, "(\\d+)\\s+days?").getMatch(0);
                        String tmphrs = new Regex(expireSecond, "(\\d+)\\s+hours?").getMatch(0);
                        String tmpmin = new Regex(expireSecond, "(\\d+)\\s+minutes?").getMatch(0);
                        String tmpsec = new Regex(expireSecond, "(\\d+)\\s+seconds?").getMatch(0);
                        long years = 0, days = 0, hours = 0, minutes = 0, seconds = 0;
                        if (!StringUtils.isEmpty(tmpYears)) {
                            years = Integer.parseInt(tmpYears);
                        }
                        if (!StringUtils.isEmpty(tmpdays)) {
                            days = Integer.parseInt(tmpdays);
                        }
                        if (!StringUtils.isEmpty(tmphrs)) {
                            hours = Integer.parseInt(tmphrs);
                        }
                        if (!StringUtils.isEmpty(tmpmin)) {
                            minutes = Integer.parseInt(tmpmin);
                        }
                        if (!StringUtils.isEmpty(tmpsec)) {
                            seconds = Integer.parseInt(tmpsec);
                        }
                        expire_milliseconds_precise_to_the_second = currentTime + ((years * 86400000 * 365) + (days * 86400000) + (hours * 3600000) + (minutes * 60000) + (seconds * 1000));
                    }
                    if (expire_milliseconds_precise_to_the_second > 0) {
                        /* This does not necessarily mean that we will use this found value later! */
                        logger.info("Successfully found precise expire-date via paymentURL: \"" + paymentURL + "\" : " + expireSecond);
                        break;
                    } else {
                        logger.info("Failed to find precise expire-date via paymentURL: \"" + paymentURL + "\"");
                    }
                }
            }
            /* Make sure that both expire-dates exist and that the precise one is not significantly lower than the 'normal' expire-date. */
            final boolean trust_expire_milliseconds_precise_to_the_second = expire_milliseconds_precise_to_the_second > 0 && expire_milliseconds_from_expiredate - expire_milliseconds_precise_to_the_second <= 24 * 60 * 60 * 1000;
            final boolean found_precise_expiredate_ONLY = expire_milliseconds_precise_to_the_second > 0 && expire_milliseconds_from_expiredate <= 0;
            if (found_precise_expiredate_ONLY) {
                logger.info("NOT using found expire_milliseconds_precise_to_the_second because we failed to find expire_milliseconds_from_expiredate --> Probably a FREE account");
            }
            if (trust_expire_milliseconds_precise_to_the_second && expire_milliseconds_from_expiredate > 0 && !found_precise_expiredate_ONLY) {
                /*
                 * 2019-07-08: Only accept precise expiredate if 'normal expiredate' is given. This eliminates failures which may otherwise
                 * happen (e.g. free accounts get recognized as premium) - examples: fileup.cc, subyshare.com, storagely.com
                 */
                /*
                 * Prefer more precise expire-date as long as it is max. 48 hours shorter than the other expire-date which is only exact up
                 * to 24 hours (up to the last day).
                 */
                logger.info("Using precise expire-date");
                expire_milliseconds = expire_milliseconds_precise_to_the_second;
            } else if (expire_milliseconds_from_expiredate > 0) {
                logger.info("Using expire-date which is up to 24 hours precise");
                expire_milliseconds = expire_milliseconds_from_expiredate;
            } else {
                logger.info("Failed to find any useful expire-date at all");
            }
            if ((expire_milliseconds - currentTime) <= 0) {
                /* If the premium account is expired or we cannot find an expire-date we'll simply accept it as a free account. */
                if (expire_milliseconds > 0) {
                    logger.info("Premium expired --> Free account");
                } else {
                    logger.info("Account is a FREE account as no expiredate has been found");
                }
                setAccountLimitsByType(account, AccountType.FREE);
            } else {
                /* Expire date is in the future --> It is a premium account */
                ai.setValidUntil(expire_milliseconds);
                setAccountLimitsByType(account, AccountType.PREMIUM);
            }
        }
        return ai;
    }

    /**
     * Tries to find apikey which, if given, usually camn be found on /?op=my_account Example host which has 'API mod' installed:
     * clicknupload.org
     */
    protected String findAPIKey(final String src) throws Exception {
        /*
         * 2019-07-11: apikey handling - prefer that instead of website. Even if an XFS website has the "API mod" enabled, we will only find
         * a key here if the user at least once pressed the "Generate API Key" button or if the XFS 'api mod' used by the website admin is
         * configured to display apikeys by default for all users.
         */
        final Pattern apikeyPattern = Pattern.compile("/api/account/info\\?key=([a-z0-9]+)");
        String apikey = new Regex(src, apikeyPattern).getMatch(0);
        String generate_apikey_url = new Regex(br.toString(), "\"([^\"]*?op=my_account[^\"]*?generate_api_key=1[^\"]*?token=[a-f0-9]{32}[^\"]*?)\"").getMatch(0);
        /*
         * 2019-07-28: If no apikey has ever been generated by the user but generate_apikey_url != null we can generate the first apikey
         * automatically.
         */
        if (apikey == null && generate_apikey_url != null) {
            if (Encoding.isHtmlEntityCoded(generate_apikey_url)) {
                /*
                 * 2019-07-28: E.g. flix555.com has "&&amp;" inside URL (= buggy) - also some XFS hosts will only allow apikey generation
                 * once and when pressing "change key" afterwards, it will always be the same. This may also be a serverside XFS bug.
                 */
                generate_apikey_url = Encoding.htmlDecode(generate_apikey_url);
            }
            logger.info("Failed to find apikey but host has api-mod enabled --> Trying to generate first apikey for this account");
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                getPage(br2, generate_apikey_url);
                apikey = br2.getRegex(apikeyPattern).getMatch(0);
            } catch (final Throwable e) {
                e.printStackTrace();
            }
            if (apikey == null) {
                logger.info("Failed to find generated apikey - possible plugin failure");
            } else {
                logger.info("Successfully found newly generated apikey");
            }
        }
        return apikey;
    }

    /**
     * Advantages over website: <br/>
     * - Always precise expire-date <br/>
     * - All info we need via one single http request <br/>
     * - Consistent
     *
     * @param anonymizeUsername
     *
     *            true: API-only mode --> Only apikey is required to be entered by the user to login but we may find his mail in the API
     *            response so we can set that as username so the user can easily identify his account in our account manager. His mail will
     *            then be set as username but anonymized e.g. "***@gmail.com". <br/>
     *            false: API is just used as a fallback in website handling which means user entered username(or mail) + password so we
     *            don't care about the given email address in the json response.
     */
    protected final AccountInfo fetchAccountInfoAPI(final Browser br, final Account account, final boolean setAndAnonymizeUsername) throws Exception {
        final AccountInfo ai = new AccountInfo();
        loginAPI(br, account);
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
        /** 2019-07-31: Better compare expire-date against their serverside time if possible! */
        final String server_timeStr = (String) entries.get("server_time");
        entries = (LinkedHashMap<String, Object>) entries.get("result");
        long expire_milliseconds_precise_to_the_second = 0;
        String email = (String) entries.get("email");
        final long currentTime;
        if (server_timeStr != null && server_timeStr.matches("\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            currentTime = TimeFormatter.getMilliSeconds(server_timeStr, "yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        } else {
            currentTime = System.currentTimeMillis();
        }
        /*
         * 2019-05-30: Seems to be a typo by the guy who develops the XFS script in the early versions of thei "API mod" :D 2019-07-28: Typo
         * is fixed in newer XFSv3 versions - still we'll keep both versions in just to make sure it will always work ...
         */
        String expireStr = (String) entries.get("premim_expire");
        if (StringUtils.isEmpty(expireStr)) {
            /* Try this too in case he corrects his mistake. */
            expireStr = (String) entries.get("premium_expire");
        }
        /*
         * 2019-08-22: For newly created free accounts, an expire-date will always be given, even if the account has never been a premium
         * account. This expire-date will usually be the creation date of the account then --> Handling will correctly recognize it as a
         * free account!
         */
        if (expireStr != null && expireStr.matches("\\d{4}\\-\\d{2}\\-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            expire_milliseconds_precise_to_the_second = TimeFormatter.getMilliSeconds(expireStr, "yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        }
        /*
         * 2019-08-22: Sadly there is no "traffic_left" value given. Upper handling will try to find it via website. Because we access
         * account-info page anyways during account-check we at least don't have to waste another http-request for that.
         */
        ai.setUnlimitedTraffic();
        /* 2019-05-30: TODO: Add support for lifetime accounts */
        if (expire_milliseconds_precise_to_the_second <= currentTime) {
            if (expire_milliseconds_precise_to_the_second > 0) {
                /*
                 * 2019-07-31: Most likely this logger will always get triggered because they will usually set the register date of new free
                 * accounts into "premium_expire".
                 */
                logger.info("Premium expired --> Free account");
            }
            /* Expired premium or no expire date given --> It is usually a Free Account */
            setAccountLimitsByType(account, AccountType.FREE);
        } else {
            /* Expire date is in the future --> It is a premium account */
            ai.setValidUntil(expire_milliseconds_precise_to_the_second);
            setAccountLimitsByType(account, AccountType.PREMIUM);
        }
        if (!StringUtils.isEmpty(email) && setAndAnonymizeUsername) {
            /* don't store the complete email as a security purpose */
            if (email.length() > 3) {
                email = "***" + email.substring(3, email.length());
            }
            account.setUser(email);
        }
        {
            /* Now set less relevant information */
            final long balance = JavaScriptEngineFactory.toLong(entries.get("balance"), 0);
            /* 2019-07-26: values can also be "inf" for "Unlimited": "storage_left":"inf" */
            // final long storage_left = JavaScriptEngineFactory.toLong(entries.get("storage_left"), 0);
            final long storage_used = JavaScriptEngineFactory.toLong(entries.get("storage_used"), 0);
            ai.setUsedSpace(storage_used);
            ai.setAccountBalance(balance);
        }
        return ai;
    }

    protected void setAccountLimitsByType(final Account account, final AccountType type) {
        account.setType(type);
        switch (type) {
        case LIFETIME:
        case PREMIUM:
            account.setConcurrentUsePossible(true);
            account.setMaxSimultanDownloads(this.getMaxSimultanPremiumDownloadNum());
            break;
        case FREE:
            account.setConcurrentUsePossible(false);
            account.setMaxSimultanDownloads(getMaxSimultaneousFreeAccountDownloads());
            break;
        case UNKNOWN:
        default:
            account.setConcurrentUsePossible(false);
            account.setMaxSimultanDownloads(1);
            break;
        }
    }

    public Form findLoginform(final Browser br) {
        Form loginform = br.getFormbyProperty("name", "FL");
        if (loginform == null) {
            /* More complicated way to find loginform ... */
            final Form[] allForms = this.br.getForms();
            for (final Form aForm : allForms) {
                final InputField inputFieldOP = aForm.getInputFieldByName("op");
                if (inputFieldOP != null && "login".equalsIgnoreCase(inputFieldOP.getValue())) {
                    loginform = aForm;
                    break;
                }
            }
        }
        return loginform;
    }

    /** Returns Form required to click on 'continue to image' for image-hosts. */
    public Form findImageForm(final Browser br) {
        final Form imghost_next_form = br.getFormbyKey("next");
        if (imghost_next_form != null && imghost_next_form.hasInputFieldByName("method_premium")) {
            imghost_next_form.remove("method_premium");
        }
        return imghost_next_form;
    }

    /** Tries to find available traffic-left value inside html code. */
    protected String regExTrafficLeft() {
        /* Traffic can also be negative! */
        String availabletraffic = new Regex(this.correctedBR, "Traffic available[^<>]*:?</TD>\\s*<TD[^>]*?>\\s*(?:<b>)?\\s*([^<>\"']+)").getMatch(0);
        if (availabletraffic == null) {
            /* 2019-02-11: For newer XFS versions */
            availabletraffic = new Regex(this.correctedBR, ">\\s*Traffic available(?:\\s*today)?\\s*</div>\\s*<div class=\"txt\\d+\">\\s*([^<>\"]+)\\s*<").getMatch(0);
        }
        return availabletraffic;
    }

    /**
     * Verifies logged-in state via multiple factors.
     *
     * @return true: Implies that user is logged-in. <br />
     *         false: Implies that user is not logged-in. A full login with login-credentials is required! <br />
     */
    public boolean isLoggedin() {
        /**
         * please use valid combinations only! login or email alone without xfss is NOT valid!
         */
        /**
         * 2019-07-25: TODO: Maybe check for valid cookies on all supported domains (e.g. special case imgrock.info and some others in
         * ImgmazeCom plugin)
         */
        final boolean login_xfss_CookieOkay = StringUtils.isAllNotEmpty(br.getCookie(getMainPage(), "login", Cookies.NOTDELETEDPATTERN), br.getCookie(getMainPage(), "xfss", Cookies.NOTDELETEDPATTERN));
        /* xfsts cookie is mostly used in xvideosharing sites (videohosters) example: vidoza.net */
        final boolean login_xfsts_CookieOkay = StringUtils.isAllNotEmpty(br.getCookie(getMainPage(), "login", Cookies.NOTDELETEDPATTERN), br.getCookie(getMainPage(), "xfsts", Cookies.NOTDELETEDPATTERN));
        /* 2019-06-21: Example website which uses rare email cookie: filefox.cc (so far the only known!) */
        final boolean email_xfss_CookieOkay = StringUtils.isAllNotEmpty(br.getCookie(getMainPage(), "email", Cookies.NOTDELETEDPATTERN), br.getCookie(getMainPage(), "xfss", Cookies.NOTDELETEDPATTERN));
        final boolean email_xfsts_CookieOkay = StringUtils.isAllNotEmpty(br.getCookie(getMainPage(), "email", Cookies.NOTDELETEDPATTERN), br.getCookie(getMainPage(), "xfsts", Cookies.NOTDELETEDPATTERN));
        /* buttons or sites that are only available for logged in users */
        final String htmlWithoutScriptTags = br.toString().replaceAll("(?s)(<script.*?</script>)", "");
        final String ahref = "<a[^<]*href\\s*=\\s*\"[^\"]*";
        final boolean logoutOkay = new Regex(htmlWithoutScriptTags, ahref + "(&|\\?)op=logout").matches() || new Regex(htmlWithoutScriptTags, ahref + "/(user_)?logout\"").matches();
        // unsafe as the find method may fail
        final boolean loginFormOkay = false && findLoginform(this.br) == null;
        // unsafe, not every site does redirect
        final boolean loginURLFailed = br.getURL().contains("op=") && br.getURL().contains("op=login");
        final boolean myAccountOkay = new Regex(htmlWithoutScriptTags, ahref + "(&|\\?)op=my_account").matches() || new Regex(htmlWithoutScriptTags, ahref + "/my(-|_)account\"").matches();
        logger.info("login_xfss_CookieOkay:" + login_xfss_CookieOkay);
        logger.info("login_xfsts_CookieOkay:" + login_xfsts_CookieOkay);
        logger.info("email_xfss_CookieOkay:" + email_xfss_CookieOkay);
        logger.info("email_xfsts_CookieOkay:" + email_xfsts_CookieOkay);
        logger.info("logoutOkay:" + logoutOkay);
        logger.info("myAccountOkay:" + myAccountOkay);
        logger.info("loginFormOkay:" + loginFormOkay);
        logger.info("loginURLFailed:" + loginURLFailed);
        final boolean ret = (login_xfss_CookieOkay || email_xfss_CookieOkay || login_xfsts_CookieOkay || email_xfsts_CookieOkay) && ((logoutOkay || loginFormOkay || myAccountOkay) && !loginURLFailed);
        logger.info("loggedin:" + ret);
        return ret;
    }

    /** Returns the full URL to the page which should contain the loginForm. */
    public String getLoginURL() {
        return getMainPage() + "/login.html";
    }

    /**
     * Returns the relative URL to the page which should contain all account information (account type, expiredate, apikey, remaining
     * traffic).
     */
    protected String getRelativeAccountInfoURL() {
        return "/?op=my_account";
    }

    /**
     * @param validateCookies
     *            true = Check whether stored cookies are still valid, if not, perform full login <br/>
     *            false = Set stored cookies and trust them if they're not older than 300000l
     *
     */
    public boolean loginWebsite(final Account account, final boolean validateCookies) throws Exception {
        synchronized (account) {
            try {
                /* Load cookies */
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                boolean validatedCookies = false;
                if (cookies != null) {
                    logger.info("Stored login-Cookies are available");
                    br.setCookies(getMainPage(), cookies);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= 300000l && !validateCookies) {
                        /* We trust these cookies as they're not that old --> Do not check them */
                        logger.info("Trust login-cookies without checking as they should still be fresh");
                        return false;
                    }
                    logger.info("Verifying login-cookies");
                    getPage(getMainPage() + getRelativeAccountInfoURL());
                    validatedCookies = isLoggedin();
                }
                if (validatedCookies) {
                    /* No additional check required --> We know cookies are valid and we're logged in --> Done! */
                    logger.info("Successfully logged in via cookies");
                } else {
                    /*
                     * 2019-08-20: Some hosts (rare case) will fail on the first attempt even with correct logindata and then demand a
                     * captcha. Example: filejoker.net
                     */
                    int login_counter = 0;
                    final int login_counter_max = 2;
                    br.clearCookies(getMainPage());
                    getPage(getLoginURL());
                    do {
                        login_counter++;
                        logger.info("Performing full login attempt: " + login_counter);
                        if (br.getHttpConnection().getResponseCode() == 404) {
                            /* Required for some XFS setups - use as common fallback. */
                            getPage(getMainPage() + "/login");
                        }
                        Form loginform = findLoginform(this.br);
                        if (loginform == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        if (loginform.hasInputFieldByName("email")) {
                            /* 2019-08-16: Very rare case e.g. filejoker.net, filefox.cc */
                            loginform.put("email", Encoding.urlEncode(account.getUser()));
                        } else {
                            loginform.put("login", Encoding.urlEncode(account.getUser()));
                        }
                        loginform.put("password", Encoding.urlEncode(account.getPass()));
                        /* Handle login-captcha if required */
                        final DownloadLink dlinkbefore = this.getDownloadLink();
                        final DownloadLink dl_dummy;
                        if (dlinkbefore != null) {
                            dl_dummy = dlinkbefore;
                        } else {
                            dl_dummy = new DownloadLink(this, "Account", this.getHost(), "https://" + account.getHoster(), true);
                            this.setDownloadLink(dl_dummy);
                        }
                        handleCaptcha(dl_dummy, loginform);
                        if (dlinkbefore != null) {
                            this.setDownloadLink(dlinkbefore);
                        }
                        submitForm(loginform);
                        if (!this.allows_multiple_login_attempts_in_one_go()) {
                            break;
                        }
                    } while (!this.isLoggedin() && login_counter <= login_counter_max);
                    if (!this.isLoggedin()) {
                        if (correctedBR.contains("op=resend_activation")) {
                            /* User entered correct logindata but hasn't activated his account yet ... */
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nYour account has not yet been activated!\r\nActivate it via the URL you should have received via E-Mail and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                        if (this.allows_multiple_login_attempts_in_one_go()) {
                            logger.info("Login failed although there were two attempts");
                        } else {
                            logger.info("Login failed - check if the website needs a captcha after the first attempt so the plugin might have to be modified via allows_multiple_login_attempts_in_one_go");
                        }
                        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername, Passwort oder login Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłędny użytkownik/hasło lub kod Captcha wymagany do zalogowania!\r\nUpewnij się, że prawidłowo wprowadziłes hasło i nazwę użytkownika. Dodatkowo:\r\n1. Jeśli twoje hasło zawiera znaki specjalne, zmień je (usuń) i spróbuj ponownie!\r\n2. Wprowadź hasło i nazwę użytkownika ręcznie bez użycia opcji Kopiuj i Wklej.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or login captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                }
                account.saveCookies(br.getCookies(getMainPage()), "");
                return true;
            } catch (final PluginException e) {
                e.printStackTrace();
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    /**
     * 2019-05-29: This is only EXPERIMENTAL! App-login: https://play.google.com/store/apps/details?id=net.sibsoft.xfsuploader <br/>
     * Around 2016 this has been implemented for some XFS websites but was never really used.It will return an XML response. Fragments of it
     * may still work for some XFS websites e.g. official DEMO website 'xfilesharing.com' and also 'europeup.com'. The login-cookie we get
     * is valid for the normal website as well! Biggest downside: Whenever a login-captcha is required (e.g. on too many wrong logins), this
     * method will NOT work!! <br/>
     * It seems like all or most of all XFS websites support this way of logging-in - even websites which were never officially supported
     * via XFS app (e.g. fileup.cc).
     */
    protected final boolean loginAPP(final Account account, boolean validateCookies) throws Exception {
        synchronized (account) {
            try {
                br.setHeader("User-Agent", "XFS-Mobile");
                br.setHeader("Content-Type", "application/x-www-form-urlencoded");
                /* Load cookies */
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                boolean validatedLoginCookies = false;
                /* 2019-08-29: Cookies will become invalid very soon so let's always verify them! */
                validateCookies = true;
                if (cookies != null) {
                    br.setCookies(getMainPage(), cookies);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= 300000l && !validateCookies) {
                        /* We trust these cookies as they're not that old --> Do not check them */
                        logger.info("Trust cookies without checking as they're still fresh");
                        return false;
                    }
                    logger.info("Verifying login-cookies");
                    getPage(getMainPage() + "/");
                    /* Missing login cookies? --> Login failed */
                    validatedLoginCookies = StringUtils.isEmpty(br.getCookie(getMainPage(), "xfss", Cookies.NOTDELETEDPATTERN));
                }
                if (validatedLoginCookies) {
                    /* No additional check required --> We know cookies are valid and we're logged in --> Done! */
                    logger.info("Successfully logged in via cookies");
                } else {
                    logger.info("Performing full login");
                    br.clearCookies(getMainPage());
                    final Form loginform = new Form();
                    loginform.setMethod(MethodType.POST);
                    loginform.setAction(getMainPage());
                    loginform.put("op", "api_get_limits");
                    loginform.put("login", Encoding.urlEncode(account.getUser()));
                    loginform.put("password", Encoding.urlEncode(account.getPass()));
                    submitForm(loginform);
                    /*
                     * Returns XML: ExtAllowed, ExtNotAllowed, MaxUploadFilesize, ServerURL[for uploads], SessionID[our login cookie],
                     * Error, SiteName, LoginLogic
                     */
                    /* Missing login cookies? --> Login failed */
                    if (StringUtils.isEmpty(br.getCookie(getMainPage(), "xfss", Cookies.NOTDELETEDPATTERN))) {
                        if (correctedBR.contains("op=resend_activation")) {
                            /* User entered correct logindata but has not activated his account ... */
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nYour account has not yet been activated!\r\nActivate it via the URL you should have received via E-Mail and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // /* Returns ballance, space, days(?premium days remaining?) - this call is not supported by all XFS sites - in this case
                // it'll return 404. */
                // final Form statsform = new Form();
                // statsform.setMethod(MethodType.POST);
                // statsform.setAction(getMainPage() + "/cgi-bin/uapi.cgi");
                // statsform.put("op", "api_get_stat");
                // submitForm(statsform);
                // final String spaceUsed = br.getRegex("<space>(\\d+\\.\\d+GB)</space>").getMatch(0);
                // final String balance = br.getRegex("<ballance>\\$(\\d+)</ballance>").getMatch(0);
                // // final String days = br.getRegex("<days>(\\d+)</days>").getMatch(0);
                // if (spaceUsed != null) {
                // account.getAccountInfo().setUsedSpace(SizeFormatter.getSize(spaceUsed));
                // }
                // if (balance != null) {
                // account.getAccountInfo().setAccountBalance(balance);
                // }
                account.saveCookies(br.getCookies(getMainPage()), "");
                validatedLoginCookies = true;
                return validatedLoginCookies;
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    /**
     * More info see supports_api()
     */
    protected final void loginAPI(final Browser br, final Account account) throws Exception {
        synchronized (account) {
            try {
                br.setCookiesExclusive(true);
                if (this.getAPIKey(account) == null) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "Invalid APIKEY - only lowercase characters and numbers are allowed!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                getPage(br, this.getMainPage() + "/api/account/info?key=" + getAPIKey(account));
                final String msg = PluginJSonUtils.getJson(br, "msg");
                final String status = PluginJSonUtils.getJson(br, "status");
                /*
                 * Returns XML: ExtAllowed, ExtNotAllowed, MaxUploadFilesize, ServerURL[for uploads], SessionID[our login cookie], Error,
                 * SiteName, LoginLogic
                 */
                /* 2019-05-30: There are no cookies at all (only "__cfduid" sometimes.) */
                final boolean jsonOK = msg != null && msg.equalsIgnoreCase("ok") && status != null && status.equals("200");
                if (!jsonOK) {
                    /* E.g. {"msg":"Wrong auth","server_time":"2019-05-29 19:29:03","status":403} */
                    /* 2019-05-29: TODO: Check for more detailed errormessages at this stage e.g. banned/blocked accounts */
                    /* 2019-05-30: Improve this errormessage - add an URL which leads directly to the users' account page. */
                    // final String errortext = String.format("Invalid APIKEY - please go to %s/?op=my_account, get your 'API URL' and enter
                    // the string behind 'key=' in JDownloader!", account.getHoster());
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            } catch (final PluginException e) {
                throw e;
            }
        }
    }

    protected final String getAPIKey(final Account account) {
        /* First check if the apikey was found via website handling and set as a property on our Account object */
        String apikey = account.getStringProperty("apikey", null);
        /* Second, maybe the user has logged in via API. */
        if (StringUtils.isEmpty(apikey) && isAPIKey(account.getUser())) {
            apikey = account.getUser();
        }
        return apikey;
    }

    protected final boolean isAPIKey(final String str) {
        if (str != null && str.matches("[a-z0-9]+")) {
            return true;
        }
        return false;
    }

    protected boolean isAccountLoginVerificationEnabled(final Account account, final boolean verifiedLogin) {
        return !verifiedLogin;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        /* Perform linkcheck without logging in */
        requestFileInformationWebsite(link, true);
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        if (AccountType.FREE.equals(account.getType())) {
            final boolean verifiedLogin = loginWebsite(account, false);
            /* Access main Content-URL */
            this.getPage(link.getPluginPatternMatcher());
            if (isAccountLoginVerificationEnabled(account, verifiedLogin) && !isLoggedin()) {
                loginWebsite(account, true);
                getPage(link.getPluginPatternMatcher());
            }
            doFree(link, account);
        } else {
            /*
             * 2019-05-21: TODO: Maybe try download right away instead of checking this here --> This could speed-up the
             * download-start-procedure!
             */
            String dllink = checkDirectLink(link, directlinkproperty);
            if (StringUtils.isEmpty(dllink)) {
                /* TODO: 2019-07-11: Consider using this over normal linkcheck whenever possible */
                // requestFileInformationAPI(link, account);
                if (this.supports_api_only_mode()) {
                    /* 2019-05-30: So far this has only been tested with videohosts */
                    /* https://xvideosharing.docs.apiary.io/#reference/file/file-direct-link/get-links-to-all-available-qualities */
                    getPage(this.getMainPage() + "/api/file/direct_link?key=" + getAPIKey(account) + "&file_code=" + this.fuid);
                    /* 2019-05-30: TODO: Check handling for password protected URLs, check errorhandling */
                    LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
                    LinkedHashMap<String, Object> entries_tmp;
                    final long status = JavaScriptEngineFactory.toLong(entries.get("status"), 0);
                    if (status != 200) {
                        /* E.g. {"msg":"no file","server_time":"2019-05-30 16:38:39","status":404} */
                        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown API server issue");
                    }
                    entries = (LinkedHashMap<String, Object>) entries.get("result");
                    /* Pick the best quality */
                    final String[] qualities = new String[] { "o", "h", "n" };
                    for (final String quality : qualities) {
                        final Object qualityO = entries.get(quality);
                        if (qualityO != null) {
                            entries_tmp = (LinkedHashMap<String, Object>) qualityO;
                            dllink = (String) entries_tmp.get("url");
                            break;
                        }
                    }
                } else {
                    final boolean verifiedLogin = loginWebsite(account, false);
                    getPage(link.getPluginPatternMatcher());
                    if (isAccountLoginVerificationEnabled(account, verifiedLogin) && !isLoggedin()) {
                        loginWebsite(account, true);
                        getPage(link.getPluginPatternMatcher());
                    }
                    dllink = getDllink(link, account);
                    if (StringUtils.isEmpty(dllink)) {
                        /* 2019-05-30: Official video download for premium users of videohosts e.g. xvideosharing.com */
                        dllink = checkOfficialVideoDownload(link, account);
                    }
                    if (StringUtils.isEmpty(dllink)) {
                        final Form dlForm = findFormF1Premium();
                        if (dlForm != null) {
                            if (isPasswordProtectedHTM()) {
                                handlePassword(dlForm, link);
                            }
                            final URLConnectionAdapter formCon = br.openFormConnection(dlForm);
                            if (formCon.isOK() && !formCon.getContentType().contains("html")) {
                                /* Very rare case - e.g. tiny-files.com */
                                handleDownload(link, account, dllink, formCon.getRequest());
                                return;
                            } else {
                                br.followConnection();
                                this.correctBR();
                            }
                            checkErrors(link, account, true);
                            dllink = getDllink(link, account);
                        } else {
                            checkErrors(link, account, true);
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                    }
                }
            }
            handleDownload(link, account, dllink, null);
        }
    }

    protected void handleDownload(final DownloadLink link, final Account account, String dllink, final Request req) throws Exception {
        final boolean resume = this.isResumeable(link, account);
        final int maxChunks = getMaxChunks(account);
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        if (req != null) {
            logger.info("Final downloadlink = Form download");
            /*
             * Save directurl before download-attempt as it should be valid even if it e.g. fails because of server issue 503 (= too many
             * connections) --> Should work fine after the next try.
             */
            final String location = req.getLocation();
            if (location != null) {
                /* E.g. redirect to downloadurl --> We can save that URL */
                link.setProperty(directlinkproperty, location);
            }
            dl = new jd.plugins.BrowserAdapter().openDownload(br, link, req, resume, maxChunks);
            handleDownloadErrors(link);
            fixFilename(link);
            try {
                /* add a download slot */
                if (account == null) {
                    controlFree(+1);
                }
                /* start the dl */
                dl.startDownload();
            } finally {
                /* remove download slot */
                if (account == null) {
                    controlFree(-1);
                }
            }
        } else {
            if (StringUtils.isEmpty(dllink) || (!dllink.startsWith("http") && !dllink.startsWith("rtmp") && !dllink.startsWith("/"))) {
                logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            logger.info("Final downloadlink = " + dllink + " starting the download...");
            if (dllink.startsWith("rtmp")) {
                /* 2019-05-21: rtmp download - VERY rare case! */
                try {
                    dl = new RTMPDownload(this, link, dllink);
                } catch (final NoClassDefFoundError e) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, "RTMPDownload class missing");
                }
                final String playpath = new Regex(dllink, "(mp4:.+)").getMatch(0);
                /* Setup rtmp connection */
                jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
                rtmp.setPageUrl(link.getPluginPatternMatcher());
                rtmp.setUrl(dllink);
                if (playpath != null) {
                    rtmp.setPlayPath(playpath);
                }
                rtmp.setFlashVer("WIN 25,0,0,148");
                rtmp.setSwfVfy("CHECK_ME");
                rtmp.setApp("vod/");
                rtmp.setResume(false);
                fixFilename(link);
                try {
                    /* add a download slot */
                    if (account == null) {
                        controlFree(+1);
                    }
                    /* start the dl */
                    ((RTMPDownload) dl).startDownload();
                } finally {
                    /* remove download slot */
                    if (account == null) {
                        controlFree(-1);
                    }
                }
            } else if (dllink.contains(".m3u8")) {
                /* 2019-08-29: HLS download - more and more streaming-hosts have this (example: streamty.com) */
                this.getPage(dllink);
                final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
                if (hlsbest == null) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown HLS streaming error");
                }
                dllink = hlsbest.getDownloadurl();
                checkFFmpeg(link, "Download a HLS Stream");
                dl = new HLSDownloader(link, br, dllink);
                try {
                    /* add a download slot */
                    if (account == null) {
                        controlFree(+1);
                    }
                    /* start the dl */
                    dl.startDownload();
                } finally {
                    /* remove download slot */
                    if (account == null) {
                        controlFree(-1);
                    }
                }
            } else {
                /*
                 * Save directurl before download-attempt as it should be valid even if it e.g. fails because of server issue 503 (= too
                 * many connections) --> Should work fine after the next try.
                 */
                link.setProperty(directlinkproperty, dllink);
                dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, resume, maxChunks);
                handleDownloadErrors(link);
                fixFilename(link);
                try {
                    /* add a download slot */
                    if (account == null) {
                        controlFree(+1);
                    }
                    /* start the dl */
                    dl.startDownload();
                } finally {
                    /* remove download slot */
                    if (account == null) {
                        controlFree(-1);
                    }
                }
            }
        }
    }

    /** Handles errors right before starting the download. */
    protected void handleDownloadErrors(final DownloadLink link) throws Exception {
        if (dl.getConnection().getContentType().contains("html")) {
            checkResponseCodeErrors(dl.getConnection());
            logger.warning("The final dllink seems not to be a file!");
            br.followConnection();
            correctBR();
            checkServerErrors();
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Final downloadlink did not lead to downloadable content");
        }
    }

    /**
     * pseudo redirect control!
     */
    @Override
    protected void runPostRequestTask(Browser ibr) throws Exception {
        final String redirect;
        if (!ibr.isFollowingRedirects() && (redirect = ibr.getRedirectLocation()) != null) {
            if (!this.isImagehoster()) {
                if (!isDllinkFile(redirect)) {
                    super.getPage(ibr, redirect);
                    return;
                }
            } else {
                if (!isDllinkImage(redirect)) {
                    super.getPage(ibr, redirect);
                    return;
                }
            }
        }
    }

    /**
     * Use this to set filename based on filename inside URL or fuid as filename either before a linkcheck happens so that there is a
     * readable filename displayed in the linkgrabber or also for mass-linkchecking as in this case these is no filename given inside HTML.
     */
    protected void setWeakFilename(final DownloadLink link) {
        final String weak_fallback_filename = this.getFallbackFilename(link);
        if (weak_fallback_filename != null) {
            link.setName(weak_fallback_filename);
        }
        /*
         * Only set MineHint if: 1. No filename at all is set OR the given name does not contain any fileextension, AND 2. We know that the
         * filehost is only hosting specific data (audio, video, pictures)!
         */
        /* TODO: Find better way to determine whether a String contains a file-extension or not. */
        final boolean fallback_filename_contains_file_extension = weak_fallback_filename != null && weak_fallback_filename.contains(".");
        final boolean setMineHint = weak_fallback_filename == null || !fallback_filename_contains_file_extension;
        if (setMineHint) {
            /* Only setMimeHint if weak filename does not contain filetype. */
            if (this.isAudiohoster()) {
                link.setMimeHint(CompiledFiletypeFilter.AudioExtensions.MP3);
            } else if (this.isImagehoster()) {
                link.setMimeHint(CompiledFiletypeFilter.ImageExtensions.JPG);
            } else if (this.internal_isVideohoster_enforce_video_filename()) {
                link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
            }
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.SibSoft_XFileShare;
    }

    /** Returns empty StringArray for filename, filesize, filehash, [more information in the future?] */
    public final String[] internal_getFileInfoArray() {
        return new String[3];
    }

    /**
     * This can 'automatically' detect whether a host supports embedding videos. <br />
     * Example: uqload.com</br>
     * Do not override - at least try to avoid having to!!
     */
    protected final boolean internal_isVideohosterEmbed() {
        return isVideohosterEmbed() || new Regex(correctedBR, "/embed-" + this.fuid + "\\.html").matches();
    }

    /**
     * Decides whether to enforce a filename with a '.mp4' ending or not. </br>
     * Names are either enforced if the configuration of the script implies this or if it detects that embedding videos is possible. </br>
     * Do not override - at least try to avoid having to!!
     */
    protected final boolean internal_isVideohoster_enforce_video_filename() {
        return internal_isVideohosterEmbed() || isVideohoster_enforce_video_filename();
    }

    /**
     * Some videohosts either have multiple qualities or even allow the user to officially download the original videofile. The problem is
     * that this may require to solve a captcha while the stream download does not. This is an unfinished method, designed to be used in the
     * future when we may have XFS plugin settings e.g. for video quality! TODO: Add quality settings
     */
    protected final boolean internal_videohost_prefer_OfficialOriginalVideoDownload() {
        return false;
    }

    /**
     * This can 'automatically' detect whether a host supports availablecheck via 'abuse' URL. <br />
     * Example: uploadboy.com</br>
     * Do not override - at least try to avoid having to!!
     */
    protected final boolean internal_supports_availablecheck_filename_abuse() {
        final boolean supported_by_hardcoded_setting = this.supports_availablecheck_filename_abuse();
        final boolean supported_by_indicating_html_code = new Regex(correctedBR, "op=report_file&(?:amp;)?id=" + this.fuid).matches();
        boolean allowed_by_auto_handling = true;
        final long last_failure = this.getPluginConfig().getLongProperty("REPORT_FILE_AVAILABLECHECK_LAST_FAILURE_TIMESTAMP", 0);
        if (last_failure > 0) {
            final long timestamp_cooldown = last_failure + internal_waittime_on_alternative_availablecheck_failures();
            if (timestamp_cooldown > System.currentTimeMillis()) {
                logger.info("internal_supports_availablecheck_filename_abuse is still deactivated as it did not work on the last attempt");
                logger.info("Time until retry: " + TimeFormatter.formatMilliSeconds(timestamp_cooldown - System.currentTimeMillis(), 0));
                allowed_by_auto_handling = false;
            }
        }
        return (supported_by_hardcoded_setting || supported_by_indicating_html_code) && allowed_by_auto_handling;
    }

    protected final boolean internal_supports_availablecheck_alt() {
        boolean allowed_by_auto_handling = true;
        final long last_failure = this.getPluginConfig().getLongProperty("ALT_AVAILABLECHECK_LAST_FAILURE_TIMESTAMP", 0);
        if (last_failure > 0) {
            final long timestamp_cooldown = last_failure + internal_waittime_on_alternative_availablecheck_failures();
            if (timestamp_cooldown > System.currentTimeMillis()) {
                logger.info("internal_supports_availablecheck_alt is still deactivated as it did not work on the last attempt");
                logger.info("Time until retry: " + TimeFormatter.formatMilliSeconds(timestamp_cooldown - System.currentTimeMillis(), 0));
                allowed_by_auto_handling = false;
            }
        }
        return supports_availablecheck_alt() && allowed_by_auto_handling;
    }

    /**
     * Defines the time to wait until a failed linkcheck method will be tried again. This should be set to > 24 hours as its purpose is to
     * minimize unnecessary http requests.
     */
    protected final long internal_waittime_on_alternative_availablecheck_failures() {
        return 7 * 24 * 60 * 60 * 1000;
    }
}