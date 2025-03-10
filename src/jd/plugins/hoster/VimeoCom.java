//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.appwork.storage.JSonStorage;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.logging2.LogInterface;
import org.appwork.utils.logging2.LogSource;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.downloader.hls.M3U8Playlist;
import org.jdownloader.plugins.components.containers.VimeoContainer;
import org.jdownloader.plugins.components.containers.VimeoContainer.Quality;
import org.jdownloader.plugins.components.containers.VimeoContainer.Source;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.http.requests.GetRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "vimeo.com" }, urls = { "decryptedforVimeoHosterPlugin://.+" })
public class VimeoCom extends PluginForHost {
    private static final String MAINPAGE        = "https://vimeo.com";
    private String              finalURL;
    public static final String  Q_MOBILE        = "Q_MOBILE";
    public static final String  Q_ORIGINAL      = "Q_ORIGINAL";
    public static final String  Q_HD            = "Q_HD";
    public static final String  Q_SD            = "Q_SD";
    public static final String  Q_BEST          = "Q_BEST";
    public static final String  SUBTITLE        = "SUBTITLE";
    private static final String CUSTOM_DATE     = "CUSTOM_DATE_3";
    private static final String CUSTOM_FILENAME = "CUSTOM_FILENAME_3";
    public static final String  ALWAYS_LOGIN    = "ALWAYS_LOGIN";
    public static final String  VVC             = "VVC_1";
    public static final String  P_240           = "P_240";
    public static final String  P_360           = "P_360";
    public static final String  P_480           = "P_480";
    public static final String  P_540           = "P_540";
    public static final String  P_720           = "P_720";
    public static final String  P_1080          = "P_1080";
    public static final String  P_1440          = "P_1440";
    public static final String  P_2560          = "P_2560";
    public static final String  ASK_REF         = "ASK_REF";

    public VimeoCom(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://vimeo.com/join");
        setConfigElements();
    }

    @Override
    public void correctDownloadLink(DownloadLink link) {
        final String url = link.getPluginPatternMatcher().replaceFirst("decryptedforVimeoHosterPlugin://", "https://");
        link.setPluginPatternMatcher(url);
    }

    @Override
    public String getAGBLink() {
        return "https://www.vimeo.com/terms";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    public static Browser prepBrGeneral(Plugin plugin, final DownloadLink dl, final Browser prepBr) {
        final String vimeo_forced_referer = dl != null ? getForcedReferer(dl) : null;
        if (vimeo_forced_referer != null) {
            prepBr.getHeaders().put("Referer", vimeo_forced_referer);
        }
        /* we do not want German headers! */
        prepBr.getHeaders().put("Accept-Language", "en-gb, en;q=0.8");
        prepBr.setAllowedResponseCodes(new int[] { 418, 451, 406 });
        prepBr.setCookiesExclusive(true);
        prepBr.clearCookies(plugin.getHost());
        prepBr.setCookie(plugin.getHost(), "language", "en");
        return prepBr;
    }

    /* API - might be useful for the future: https://github.com/bromix/plugin.video.vimeo/blob/master/resources/lib/vimeo/client.py */
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        br = prepBrGeneral(this, downloadLink, br);
        setBrowserExclusive();
        if (downloadLink.getBooleanProperty("offline", false)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String downloadlinkId = downloadLink.getLinkID().replaceFirst("_ORIGINAL$", "");
        final String videoQuality = downloadLink.getStringProperty("videoQuality", null);
        final boolean isSubtitle = (StringUtils.endsWithCaseInsensitive(videoQuality, "SUBTITLE") || StringUtils.containsIgnoreCase(videoQuality, "_SUBTITLE_")) || (StringUtils.endsWithCaseInsensitive(downloadlinkId, "SUBTITLE") || StringUtils.containsIgnoreCase(downloadlinkId, "_SUBTITLE_"));
        final boolean isHLS = StringUtils.endsWithCaseInsensitive(videoQuality, "HLS") || StringUtils.endsWithCaseInsensitive(downloadlinkId, "HLS");
        final boolean isDownload = StringUtils.endsWithCaseInsensitive(videoQuality, "DOWNLOAD") || StringUtils.endsWithCaseInsensitive(downloadlinkId, "DOWNLOAD");
        br.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        finalURL = downloadLink.getStringProperty("directURL", null);
        if (finalURL != null) {
            try {
                final Browser brc = br.cloneBrowser();
                brc.getHeaders().put("Accept-Encoding", "identity");
                /* Some videos are hosted on Amazon S3, don't use head requests for this reason */
                con = brc.openHeadConnection(finalURL);
                if (con.isOK() && StringUtils.containsIgnoreCase(con.getContentType(), "vnd.apple.mpegurl") && isHLS) {
                    downloadLink.setFinalFileName(getFormattedFilename(downloadLink));
                    return AvailableStatus.TRUE;
                } else if (con.isOK() && StringUtils.containsIgnoreCase(con.getContentType(), "vtt") && isSubtitle) {
                    if (con.getLongContentLength() > 0) {
                        downloadLink.setVerifiedFileSize(con.getLongContentLength());
                    }
                    downloadLink.setFinalFileName(getFormattedFilename(downloadLink));
                    return AvailableStatus.TRUE;
                    // } else if (con.isOK() && StringUtils.containsIgnoreCase(con.getContentType(), "mp4") && isDownload) {
                } else if (con.isOK() && StringUtils.containsIgnoreCase(con.getContentType(), "mp4")) {
                    if (con.getLongContentLength() > 0) {
                        downloadLink.setVerifiedFileSize(con.getLongContentLength());
                    }
                    downloadLink.setFinalFileName(getFormattedFilename(downloadLink));
                    return AvailableStatus.TRUE;
                } else {
                    /* directURL no longer valid */
                    finalURL = null;
                    downloadLink.setProperty("directURL", Property.NULL);
                }
            } catch (final IOException e) {
                downloadLink.setProperty("directURL", Property.NULL);
                throw e;
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }
        final String videoID = getVideoID(downloadLink);
        if (videoID == null) {
            /* This should never happen! */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        br = prepBrGeneral(this, downloadLink, new Browser());
        br.setFollowRedirects(true);
        final String forced_referer = getForcedReferer(downloadLink);
        final AtomicReference<String> referer = new AtomicReference<String>(forced_referer);
        final boolean alwaysLogin = getPluginConfig().getBooleanProperty(VimeoCom.ALWAYS_LOGIN, false);
        final Account account = (alwaysLogin || (Thread.currentThread() instanceof SingleDownloadController)) ? AccountController.getInstance().getValidAccount(this) : null;
        Object lock = new Object();
        if (account != null) {
            try {
                login(this, br, account);
                lock = account;
            } catch (PluginException e) {
                logger.log(e);
                final LogInterface logger = getLogger();
                if (logger instanceof LogSource) {
                    handleAccountException(account, (LogSource) logger, e);
                } else {
                    handleAccountException(account, null, e);
                }
            }
        }
        synchronized (lock) {
            accessVimeoURL(this, this.br, downloadLink.getPluginPatternMatcher(), referer, getVimeoUrlType(downloadLink));
            handlePW(downloadLink, br);
            /* Video titles can be changed afterwards by the puloader - make sure that we always got the currrent title! */
            String videoTitle = null;
            try {
                final String json = jd.plugins.decrypter.VimeoComDecrypter.getJsonFromHTML(this.br);
                LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(json);
                entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.walkJson(entries, "vimeo_esi/config/clipData");
                if (entries != null) {
                    videoTitle = (String) entries.get("title");
                }
            } catch (final Throwable e) {
            }
            // now we nuke linkids for videos.. crazzy... only remove the last one, _ORIGINAL comes from variant system
            final boolean isStream = !isHLS && !isDownload && !isSubtitle;
            final List<VimeoContainer> qualities = find(this, getVimeoUrlType(downloadLink), br, videoID, isDownload || !isHLS, isStream, isHLS, isSubtitle);
            if (qualities.isEmpty()) {
                logger.warning("vimeo.com: Qualities could not be found");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            VimeoContainer container = null;
            if (downloadlinkId != null) {
                for (VimeoContainer quality : qualities) {
                    final String linkdupeid = quality.createLinkID(videoID);
                    // match refreshed qualities to stored reference, to make sure we have the same format for resume! we never want to
                    // cross
                    // over!
                    if (StringUtils.equalsIgnoreCase(linkdupeid, downloadlinkId)) {
                        container = quality;
                        break;
                    }
                }
            }
            if (container == null && videoQuality != null) {
                for (VimeoContainer quality : qualities) {
                    // match refreshed qualities to stored reference, to make sure we have the same format for resume! we never want to
                    // cross
                    // over!
                    if (videoQuality.equalsIgnoreCase(quality.getQuality().toString())) {
                        container = quality;
                        break;
                    }
                }
            }
            if (container == null || container.getDownloadurl() == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            finalURL = container.getDownloadurl();
            switch (container.getSource()) {
            case DOWNLOAD:
            case WEB:
            case SUBTITLE:
                try {
                    /* Some videos are hosted on Amazon S3, don't use head requests for this reason */
                    con = br.openGetConnection(finalURL);
                    if (StringUtils.containsIgnoreCase(con.getContentType(), "json") || StringUtils.containsIgnoreCase(finalURL, "cold_request=1")) {
                        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Defrosting download, please wait", 30 * 60 * 1000l);
                    } else if (!StringUtils.containsIgnoreCase(con.getContentType(), "html") && con.isOK()) {
                        if (con.getLongContentLength() > 0) {
                            downloadLink.setVerifiedFileSize(con.getLongContentLength());
                        }
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                } finally {
                    if (con != null) {
                        con.disconnect();
                    }
                }
                downloadLink.setProperty("directURL", finalURL);
                break;
            case HLS:
                if (container.getEstimatedSize() != null) {
                    downloadLink.setDownloadSize(container.getEstimatedSize());
                }
                break;
            default:
                break;
            }
            if (!StringUtils.isEmpty(videoTitle)) {
                downloadLink.setProperty("videoTitle", videoTitle);
            }
            downloadLink.setFinalFileName(getFormattedFilename(downloadLink));
            return AvailableStatus.TRUE;
        }
    }

    public static String getVideoID(final DownloadLink dl) {
        return dl.getStringProperty("videoID", null);
    }

    private String getUnlistedHash(final DownloadLink dl) {
        return dl.getStringProperty("specialVideoID", null);
    }

    public static enum VIMEO_URL_TYPE {
        RAW,
        PLAYER,
        UNLISTED,
        NORMAL
    }

    public static VIMEO_URL_TYPE getUrlType(final String url) {
        if (url != null) {
            final String unlistedHash = jd.plugins.decrypter.VimeoComDecrypter.getUnlistedHashFromURL(url);
            if (unlistedHash != null) {
                return VIMEO_URL_TYPE.UNLISTED;
            } else if (url.matches("^https?://player\\.vimeo.com/.+")) {
                return VIMEO_URL_TYPE.PLAYER;
            }
        }
        return VIMEO_URL_TYPE.RAW;
    }

    /**
     * Use this to access a vimeo URL for the first time! Make sure to call password handling afterwards! <br />
     * Important: Execute password handling afterwards!!
     */
    public static VIMEO_URL_TYPE accessVimeoURL(final Plugin plugin, final Browser br, final String url_source, final AtomicReference<String> forced_referer, final VIMEO_URL_TYPE urlTypeRequested) throws Exception {
        final String videoID = jd.plugins.decrypter.VimeoComDecrypter.getVideoidFromURL(url_source);
        final String unlistedHash = jd.plugins.decrypter.VimeoComDecrypter.getUnlistedHashFromURL(url_source);
        // final String reviewHash = jd.plugins.decrypter.VimeoComDecrypter.getReviewHashFromURL(url_source);
        final String referer = forced_referer != null ? forced_referer.get() : null;
        if (referer != null) {
            plugin.getLogger().info("Referer:" + referer);
            br.getHeaders().put("Referer", referer);
        }
        plugin.getLogger().info("urlTypeRequested:" + urlTypeRequested);
        final VIMEO_URL_TYPE ret;
        if (urlTypeRequested == VIMEO_URL_TYPE.RAW || (urlTypeRequested == null && url_source.matches("https?://.*?vimeo\\.com.*?/review/.+")) || videoID == null) {
            /*
             * 2019-02-20: Special: We have to access 'review' URLs same way as via browser - if we don't, we will get response 403/404!
             * Review-URLs may contain a reviewHash which is required! If then, inside their json, the unlistedHash is present,
             */
            ret = getUrlType(url_source);
            plugin.getLogger().info("getUrlType:" + url_source + "->" + ret);
            br.getPage(url_source);
        } else if (unlistedHash == null && (urlTypeRequested == VIMEO_URL_TYPE.PLAYER || (urlTypeRequested == null && referer != null))) {
            ret = VIMEO_URL_TYPE.PLAYER;
            br.getPage("https://player.vimeo.com/video/" + videoID);
        } else if (unlistedHash != null && (urlTypeRequested == VIMEO_URL_TYPE.UNLISTED || urlTypeRequested == null)) {
            ret = VIMEO_URL_TYPE.UNLISTED;
            br.getPage(String.format("https://vimeo.com/%s/%s", videoID, unlistedHash));
            if (jd.plugins.decrypter.VimeoComDecrypter.iranWorkaround(br, videoID) && br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        } else {
            if (unlistedHash != null) {
                ret = VIMEO_URL_TYPE.UNLISTED;
            } else {
                ret = VIMEO_URL_TYPE.NORMAL;
            }
            if (unlistedHash != null) {
                br.getPage(String.format("https://vimeo.com/%s/%s", videoID, unlistedHash));
            } else {
                br.getPage("https://vimeo.com/" + videoID);
            }
        }
        if (br.getHttpConnection().getResponseCode() == 403) {
            // referer or account might be required
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, "403");
        } else if (br.getHttpConnection().getResponseCode() == 404 || "This video does not exist\\.".equals(PluginJSonUtils.getJsonValue(br, "message"))) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, "Video does not exist");
        } else if (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 451) {
            // HTTP/1.1 451 Unavailable For Legal Reasons
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, "451");
        } else if (br.containsHTML(">There was a problem loading this video")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, "Problem loading this video");
        } else if (br.containsHTML(">\\s*Private Video on Vimeo\\s*<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, "Private video");
        }
        final String vuid = br.getRegex("document\\.cookie\\s*=\\s*'vuid='\\s*\\+\\s*encodeURIComponent\\('(\\d+\\.\\d+)'\\)").getMatch(0);
        if (vuid != null) {
            br.setCookie(br.getURL(), "vuid", vuid);
        }
        return ret;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        doFree(downloadLink);
    }

    public void doFree(final DownloadLink downloadLink) throws Exception {
        if (finalURL == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getPage(downloadLink.getDownloadURL());
        if (!finalURL.contains(".m3u8")) {
            dl = new jd.plugins.BrowserAdapter().openDownload(br, downloadLink, finalURL, true, 0);
            if (StringUtils.containsIgnoreCase(dl.getConnection().getContentType(), "html") || StringUtils.containsIgnoreCase(dl.getConnection().getContentType(), "json")) {
                logger.warning("The final dllink seems not to be a file!");
                try {
                    br.followConnection();
                } catch (final IOException e) {
                    logger.log(e);
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        } else {
            // hls
            dl = new HLSDownloader(downloadLink, br, finalURL);
        }
        dl.startDownload();
    }

    public static final String VIMEOURLTYPE = "VIMEOURLTYPE";

    protected VIMEO_URL_TYPE getVimeoUrlType(final DownloadLink link) {
        final String urlType = link.getStringProperty(VIMEOURLTYPE, null);
        if (urlType != null) {
            try {
                return VIMEO_URL_TYPE.valueOf(urlType);
            } catch (Throwable ignore) {
                logger.log(ignore);
            }
        }
        return getUrlType(link.getPluginPatternMatcher());
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        handleFree(link);
    }

    @SuppressWarnings("deprecation")
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        synchronized (account) {
            final AccountInfo ai = new AccountInfo();
            if (!account.getUser().matches(".+@.+\\..+")) {
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBitte gib deine E-Mail Adresse ins Benutzername Feld ein!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlease enter your e-mail adress in the username field!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            setBrowserExclusive();
            login(this, br, account);
            if (br.getRequest() == null || !StringUtils.containsIgnoreCase(br.getHost(), "vimeo.com")) {
                br.getPage(MAINPAGE);
            }
            br.getPage("/settings");
            String type = br.getRegex("acct_status\">.*?>(.*?)<").getMatch(0);
            if (type == null) {
                type = br.getRegex("user_type'\\s*,\\s*'(.*?)'").getMatch(0);
                if (type == null) {
                    type = br.getRegex("\"user_type\"\\s*:\\s*\"(.*?)\"").getMatch(0);
                }
            }
            if (type != null) {
                ai.setStatus(type);
            } else {
                ai.setStatus(null);
            }
            account.setValid(true);
            ai.setUnlimitedTraffic();
            return ai;
        }
    }

    public static boolean isLoggedIn(Browser br) {
        if (br.getCookie(MAINPAGE, "vuid", Cookies.NOTDELETEDPATTERN) == null) {
            return false;
        } else if (!"1".equals(br.getCookie(MAINPAGE, "is_logged_in", Cookies.NOTDELETEDPATTERN))) {
            return false;
        } else if (br.getCookie(MAINPAGE, "vimeo", Cookies.NOTDELETEDPATTERN) == null) {
            return false;
        } else {
            return true;
        }
    }

    public static void login(final Plugin plugin, Browser br, Account account) throws PluginException, IOException {
        synchronized (account) {
            try {
                br = prepBrGeneral(plugin, null, br);
                br.setFollowRedirects(true);
                Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    br.setCookies(MAINPAGE, cookies);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= 1 * 60 * 1000l) {
                        /* We trust these cookies --> Do not check them */
                        return;
                    }
                    br.getPage(MAINPAGE);
                    if (!isLoggedIn(br)) {
                        cookies = null;
                    }
                }
                if (cookies == null) {
                    br.getPage("https://www.vimeo.com/log_in");
                    final String xsrft = getXsrft(br);
                    // static post are bad idea, always use form.
                    final Form login = br.getFormbyProperty("id", "login_form");
                    if (login == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    login.put("token", Encoding.urlEncode(xsrft));
                    login.put("email", Encoding.urlEncode(account.getUser()));
                    login.put("password", Encoding.urlEncode(account.getPass()));
                    br.submitForm(login);
                    if (br.getHttpConnection().getResponseCode() == 406) {
                        throw new AccountUnavailableException("Account login temp. blocked", 15 * 60 * 1000l);
                    }
                    if (!isLoggedIn(br)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(br.getCookies(MAINPAGE), "");
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    public static final String getXsrft(Browser br) throws PluginException {
        String xsrft = br.getRegex("vimeo\\.xsrft\\s*=\\s*('|\"|)([a-z0-9\\.]{32,})\\1").getMatch(1);
        if (xsrft == null) {
            xsrft = br.getRegex("\"xsrft\"\\s*:\\s*\"([a-z0-9\\.]{32,})\"").getMatch(0);
            if (xsrft == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        return xsrft;
    }

    public static boolean isPasswordProtected(final Browser br) throws PluginException {
        return br.containsHTML("\\d+/password");
    }

    /** Handles password protected URLs - usually correct password will already be given via decrypter handling! */
    private void handlePW(final DownloadLink downloadLink, final Browser br) throws Exception {
        if (isPasswordProtected(br)) {
            final String xsrft = getXsrft(br);
            final Form pwform = jd.plugins.decrypter.VimeoComDecrypter.getPasswordForm(br);
            if (pwform == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            String passCode = downloadLink.getDownloadPassword();
            if (passCode == null) {
                passCode = Plugin.getUserInput("Password?", downloadLink);
                if (passCode == null) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, "Password needed!");
                }
            }
            pwform.put("token", xsrft);
            pwform.put("password", Encoding.urlEncode(passCode));
            br.submitForm(pwform);
            if (isPasswordProtected(br)) {
                downloadLink.setDownloadPassword(null);
                throw new PluginException(LinkStatus.ERROR_FATAL, "Password needed!");
            }
            downloadLink.setDownloadPassword(passCode);
        }
    }

    @SuppressWarnings({ "unchecked", "unused" })
    public static List<VimeoContainer> find(final Plugin plugin, final VIMEO_URL_TYPE urlTypeUsed, final Browser ibr, final String ID, final boolean user_wants_download_urls, final boolean stream, final boolean hls, final boolean subtitles) throws Exception {
        /*
         * little pause needed so the next call does not return trash
         */
        plugin.getLogger().info("urlTypeUsed:" + urlTypeUsed);
        Thread.sleep(1000);
        boolean debug = false;
        String configURL = ibr.getRegex("data-config-url=\"(https?://player\\.vimeo\\.com/(v2/)?video/\\d+/config.*?)\"").getMatch(0);
        if (StringUtils.isEmpty(configURL)) {
            /* can be within json on the given page now.. but this is easy to just request again raz20151215 */
            configURL = PluginJSonUtils.getJsonValue(ibr, "config_url");
            if (StringUtils.isEmpty(configURL)) {
                /* 2019-02-20 */
                configURL = PluginJSonUtils.getJsonValue(ibr, "configUrl");
            }
        }
        final ArrayList<VimeoContainer> results = new ArrayList<VimeoContainer>();
        /**
         * "download_config":[] --> Download possible, "download_config":null --> No download available. <br />
         * 2019-04-30: Problem: On player.vimeo.com, this property is not given so we either have to visit the main video page just to find
         * out about this information or simply try it (current attempt). <br />
         * No matter which attempt we chose: We need one request more!
         */
        boolean download_might_be_possible = false;
        if (ibr.getURL().contains("player.vimeo.com/")) {
            /*
             * 2019-04-30: We've already accessed the player-page which means we can only assume whether a download might be possible or not
             * based on the added linktype.
             */
            final boolean force_download_attempt = false;
            if (urlTypeUsed == null || urlTypeUsed == VIMEO_URL_TYPE.RAW || urlTypeUsed == VIMEO_URL_TYPE.NORMAL || force_download_attempt) {
                download_might_be_possible = true;
            } else {
                download_might_be_possible = false;
            }
        } else {
            /* As stated in the other case, if we access the main video page first, it should contain this information. */
            final String file_transfer_url = PluginJSonUtils.getJson(ibr, "file_transfer_url");
            download_might_be_possible = file_transfer_url != null;
            if (!download_might_be_possible) {
                try {
                    final String json = jd.plugins.decrypter.VimeoComDecrypter.getJsonFromHTML(ibr);
                    final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(json);
                    /* Empty Array = download possible, null = download NOT possible! */
                    final Object download_might_be_possibleO = entries.get("download_config");
                    plugin.getLogger().info("download_config:" + download_might_be_possibleO);
                    download_might_be_possible = download_might_be_possibleO != null;
                } catch (final Throwable e) {
                    plugin.getLogger().log(e);
                }
            } else {
                plugin.getLogger().info("file_transfer_url:" + file_transfer_url);
            }
        }
        plugin.getLogger().info("Download possible:" + download_might_be_possible);
        if (user_wants_download_urls && download_might_be_possible) {
            plugin.getLogger().info("query downloads");
            results.addAll(handleDownloadConfig(plugin, ibr, ID));
            plugin.getLogger().info("downloads found:" + results.size());
        }
        /** 2019-04-30: Only try to grab streams if we failed to find any downloads. */
        final boolean tryToFindStreams = results.size() < 2 && (stream || hls);
        /* player.vimeo.com links = Special case as the needed information is already in our current browser. */
        if ((tryToFindStreams || subtitles) && (configURL != null || ibr.getURL().contains("player.vimeo.com/"))) {
            plugin.getLogger().info("try to find streams");
            // iconify_down_b could fail, revert to the following if statements.
            String json = null;
            if (configURL != null) {
                final Browser brc = ibr.cloneBrowser();
                brc.getHeaders().put("Accept", "*/*");
                configURL = configURL.replaceAll("&amp;", "&");
                Thread.sleep(100);
                json = brc.getPage(configURL);
            } else {
                json = ibr.getRegex("a\\s*=\\s*(\\s*\\{\\s*\"cdn_url\".*?);if\\(\\!?a\\.request\\)").getMatch(0);
                if (json == null) {
                    json = ibr.getRegex("t\\s*=\\s*(\\s*\\{\\s*\"cdn_url\".*?);if\\(\\!?t\\.request\\)").getMatch(0);
                    if (json == null) {
                        json = ibr.getRegex("^(\\s*\\{\\s*\"cdn_url\".+)").getMatch(0);
                        if (json == null) {
                            json = ibr.getRegex("(\\s*\\{\\s*\"cdn_url\".*?\\});").getMatch(0);
                        }
                    }
                }
            }
            /* Old handling without DummyScriptEnginePlugin removed AFTER revision 28754 */
            if (json != null) {
                final Map<String, Object> entries = (Map<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
                final Map<String, Object> files = (Map<String, Object>) JavaScriptEngineFactory.walkJson(entries, "request/files");
                final List<Map<String, Object>> text_tracks = (List<Map<String, Object>>) JavaScriptEngineFactory.walkJson(entries, "request/text_tracks");
                // progressive = web, hls = hls
                if (files != null) {
                    if (files.containsKey("progressive") && stream) {
                        final int before = results.size();
                        plugin.getLogger().info("query progressive streams");
                        results.addAll(handleProgessive(plugin, ibr, files));
                        plugin.getLogger().info("progressive streams found:" + (results.size() - before));
                    }
                    if (files.containsKey("hls") && hls) {
                        final int before = results.size();
                        plugin.getLogger().info("query hls streams");
                        results.addAll(handleHLS(plugin, ibr, (Map<String, Object>) files.get("hls")));
                        plugin.getLogger().info("hls streams found:" + (results.size() - before));
                    }
                }
                if (text_tracks != null && subtitles) {
                    final int before = results.size();
                    plugin.getLogger().info("query subtitles");
                    results.addAll(handleSubtitles(plugin, ibr, text_tracks));
                    plugin.getLogger().info("subtitles found:" + (results.size() - before));
                }
            }
        }
        return results;
    }

    private static List<VimeoContainer> handleSubtitles(Plugin plugin, Browser br, List<Map<String, Object>> text_tracks) {
        final ArrayList<VimeoContainer> ret = new ArrayList<VimeoContainer>();
        try {
            for (final Map<String, Object> text_track : text_tracks) {
                final VimeoContainer vvc = new VimeoContainer();
                final String url = (String) text_track.get("url");
                final String lang = (String) text_track.get("lang");
                if (url == null || lang == null) {
                    continue;
                }
                vvc.setSource(Source.SUBTITLE);
                vvc.setDownloadurl(br.getURL(url).toString());
                vvc.setLang(lang);
                final Number id = getNumber(text_track, "id");
                if (id != null) {
                    vvc.setId(id.longValue());
                }
                vvc.setExtension(".srt");
                ret.add(vvc);
            }
        } catch (final Throwable t) {
            plugin.getLogger().log(t);
        }
        return ret;
    }

    private static Number getNumber(Map<String, Object> map, String key) {
        final Object value = map.get(key);
        if (value instanceof Number) {
            return (Number) value;
        } else if (value instanceof String && ((String) value).matches("^\\d+&")) {
            return Long.parseLong(value.toString());
        } else if (value instanceof String) {
            return SizeFormatter.getSize(value.toString());
        } else {
            return null;
        }
    }

    /** Crawls official downloadURLs if available */
    private static List<VimeoContainer> handleDownloadConfig(Plugin plugin, final Browser ibr, final String ID) throws InterruptedException {
        final ArrayList<VimeoContainer> ret = new ArrayList<VimeoContainer>();
        try {
            Thread.sleep(500);
            final Browser brc = ibr.cloneBrowser();
            final GetRequest request = brc.createGetRequest("https://" + plugin.getHost() + "/" + ID + "?action=load_download_config");
            request.getHeaders().put("Accept", "*/*");
            request.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            request.getHeaders().put("Cache-Control", "no-cache");
            request.getHeaders().put("Pragma", "no-cache");
            request.getHeaders().put("Connection", "closed");
            final String json = brc.getPage(request);
            final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(json);
            if (entries != null) {
                final ArrayList<Object> official_downloads_all = new ArrayList<Object>();
                final ArrayList<Object> official_downloads_streams = (ArrayList<Object>) entries.get("files");
                final Object official_download_single_original = entries.get("source_file");
                if (official_downloads_streams != null) {
                    official_downloads_all.addAll(official_downloads_streams);
                }
                if (official_download_single_original != null) {
                    official_downloads_all.add(official_download_single_original);
                }
                for (final Object file : official_downloads_all) {
                    final Map<String, Object> info = (Map<String, Object>) file;
                    final boolean is_source = ((Boolean) info.get("is_source")).booleanValue();
                    final VimeoContainer vvc = new VimeoContainer();
                    vvc.setDownloadurl((String) info.get("download_url"));
                    final String ext = (String) info.get("extension");
                    if (StringUtils.isNotEmpty(ext)) {
                        vvc.setExtension("." + ext);
                    } else {
                        vvc.setExtension();
                    }
                    vvc.setWidth(((Number) info.get("width")).intValue());
                    vvc.setHeight(((Number) info.get("height")).intValue());
                    final Number fileSize = getNumber(info, "size");
                    if (fileSize != null) {
                        vvc.setFilesize(fileSize.longValue());
                    }
                    vvc.setSource(Source.DOWNLOAD);
                    if (is_source) {
                        vvc.setQuality(Quality.ORIGINAL);
                    } else {
                        final String sd = (String) info.get("public_name");
                        if ("sd".equals(sd)) {
                            vvc.setQuality(Quality.SD);
                        } else if ("hd".equals(sd)) {
                            vvc.setQuality(Quality.HD);
                        } else {
                            // not provided... determine by x and y
                            vvc.setQuality();
                        }
                    }
                    ret.add(vvc);
                }
            }
        } catch (final InterruptedException e) {
            plugin.getLogger().log(e);
            throw e;
        } catch (final Throwable t) {
            plugin.getLogger().log(t);
        }
        return ret;
    }

    @Override
    public boolean isSpeedLimited(DownloadLink link, Account account) {
        return false;
    }

    /** Handles http streams (stream download!) */
    private static List<VimeoContainer> handleProgessive(Plugin plugin, Browser br, final Map<String, Object> files) {
        final ArrayList<VimeoContainer> ret = new ArrayList<VimeoContainer>();
        try {
            final ArrayList<Object> progressive = (ArrayList<Object>) files.get("progressive");
            // atm they only have one object in array [] and then wrapped in {}
            for (final Object obj : progressive) {
                // todo some code to map...
                final LinkedHashMap<String, Object> abc = (LinkedHashMap<String, Object>) obj;
                final VimeoContainer vvc = new VimeoContainer();
                vvc.setDownloadurl((String) abc.get("url"));
                vvc.setExtension();
                vvc.setHeight(((Number) abc.get("height")).intValue());
                vvc.setWidth(((Number) abc.get("width")).intValue());
                final Object o_bitrate = abc.get("bitrate");
                if (o_bitrate != null) {
                    /* Bitrate is 'null' for vp6 codec */
                    vvc.setBitrate(((Number) o_bitrate).intValue());
                }
                final String quality = (String) abc.get("quality");
                vvc.setQuality(vvc.getHeight() >= 720 ? Quality.HD : Quality.SD);
                if (StringUtils.containsIgnoreCase(quality, "720") || StringUtils.containsIgnoreCase(quality, "1080")) {
                    vvc.setQuality(Quality.HD);
                }
                vvc.setCodec(".mp4".equalsIgnoreCase(vvc.getExtension()) ? "h264" : "vp5");
                final Number id = getNumber(abc, "id");
                if (id != null) {
                    vvc.setId(id.longValue());
                }
                vvc.setSource(Source.WEB);
                ret.add(vvc);
            }
        } catch (final Throwable t) {
            plugin.getLogger().log(t);
        }
        return ret;
    }

    private static List<VimeoContainer> handleHLS(Plugin plugin, final Browser br, final Map<String, Object> base) {
        final ArrayList<VimeoContainer> ret = new ArrayList<VimeoContainer>();
        try {
            // they can have audio and video seperated (usually for dash);
            final String defaultCDN = (String) base.get("default_cdn");
            final String m3u8 = (String) JavaScriptEngineFactory.walkJson(base, defaultCDN != null ? "cdns/" + defaultCDN + "/url" : "cdns/{0}/url");
            final List<HlsContainer> qualities = HlsContainer.getHlsQualities(br, m3u8);
            long duration = -1;
            for (final HlsContainer quality : qualities) {
                if (duration == -1) {
                    duration = 0;
                    final List<M3U8Playlist> m3u8s = quality.getM3U8(br.cloneBrowser());
                    duration = M3U8Playlist.getEstimatedDuration(m3u8s);
                }
                final VimeoContainer container = VimeoContainer.createVimeoVideoContainer(quality);
                final int bandwidth;
                if (quality.getAverageBandwidth() > 0) {
                    bandwidth = quality.getAverageBandwidth();
                } else {
                    bandwidth = quality.getBandwidth();
                }
                if (duration > 0 && bandwidth > 0) {
                    final long estimatedSize = bandwidth / 8 * (duration / 1000);
                    container.setEstimatedSize(estimatedSize);
                }
                ret.add(container);
            }
        } catch (final Throwable t) {
            plugin.getLogger().log(t);
        }
        return ret;
    }

    public static VimeoContainer getVimeoVideoContainer(final DownloadLink downloadLink, final boolean allowNull) throws Exception {
        synchronized (downloadLink) {
            final Object value = downloadLink.getProperty(VVC, null);
            if (value instanceof VimeoContainer) {
                return (VimeoContainer) value;
            } else if (value instanceof String) {
                final VimeoContainer ret = JSonStorage.restoreFromString(value.toString(), VimeoContainer.TYPE_REF);
                downloadLink.setProperty(VVC, ret);
                return ret;
            } else if (value instanceof Map) {
                final VimeoContainer ret = JSonStorage.restoreFromString(JSonStorage.toString(value), VimeoContainer.TYPE_REF);
                downloadLink.setProperty(VVC, ret);
                return ret;
            } else {
                if (allowNull) {
                    return null;
                } else {
                    if (value != null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, null, -1, new Exception(value.getClass().getSimpleName()));
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    public String getFormattedFilename(final DownloadLink downloadLink) throws Exception {
        final VimeoContainer vvc = getVimeoVideoContainer(downloadLink, true);
        String videoTitle = downloadLink.getStringProperty("videoTitle", null);
        final SubConfiguration cfg = SubConfiguration.getConfig("vimeo.com");
        String formattedFilename = cfg.getStringProperty(CUSTOM_FILENAME, defaultCustomFilename);
        if (formattedFilename == null || formattedFilename.equals("")) {
            formattedFilename = defaultCustomFilename;
        }
        if (!formattedFilename.contains("*videoname") && !formattedFilename.contains("*ext*") && !formattedFilename.contains("*videoid*")) {
            formattedFilename = defaultCustomFilename;
        }
        final String date = downloadLink.getStringProperty("originalDate", null);
        final String channelName = downloadLink.getStringProperty("channel", null);
        final String videoID = downloadLink.getStringProperty("videoID", null);
        final String videoQuality;
        final String videoFrameSize;
        final String videoBitrate;
        final String videoType;
        final String videoExt;
        if (vvc != null) {
            if (VimeoContainer.Source.SUBTITLE.equals(vvc.getSource())) {
                videoQuality = null;
                videoFrameSize = "";
                videoBitrate = "";
                videoType = vvc.getLang();
            } else {
                videoQuality = vvc.getQuality().toString();
                videoFrameSize = vvc.getWidth() + "x" + vvc.getHeight();
                videoBitrate = vvc.getBitrate() == -1 ? "" : String.valueOf(vvc.getBitrate());
                videoType = String.valueOf(vvc.getSource());
            }
            videoExt = vvc.getExtension();
        } else {
            videoQuality = downloadLink.getStringProperty("videoQuality", null);
            videoFrameSize = downloadLink.getStringProperty("videoFrameSize", "");
            videoBitrate = downloadLink.getStringProperty("videoBitrate", "");
            videoType = downloadLink.getStringProperty("videoType", null);
            videoExt = downloadLink.getStringProperty("videoExt", null);
        }
        String formattedDate = null;
        if (date != null) {
            final String userDefinedDateFormat = cfg.getStringProperty(CUSTOM_DATE, defaultCustomDate);
            SimpleDateFormat formatter = getFormatterForDate(date);
            final Date dateStr;
            dateStr = formatter.parse(date);
            formattedDate = formatter.format(dateStr);
            Date theDate = formatter.parse(formattedDate);
            if (userDefinedDateFormat != null) {
                try {
                    formatter = new SimpleDateFormat(userDefinedDateFormat);
                    formattedDate = formatter.format(theDate);
                } catch (Exception e) {
                    // prevent user error killing plugin, use input-data as fallback.
                    formattedDate = date;
                }
            }
        }
        if (formattedDate != null) {
            formattedFilename = formattedFilename.replace("*date*", formattedDate);
        } else {
            formattedFilename = formattedFilename.replace("*date*", "");
        }
        if (formattedFilename.contains("*videoid*")) {
            formattedFilename = formattedFilename.replace("*videoid*", videoID);
        }
        if (formattedFilename.contains("*channelname*")) {
            if (channelName != null) {
                formattedFilename = formattedFilename.replace("*channelname*", channelName);
            } else {
                formattedFilename = formattedFilename.replace("*channelname*", "");
            }
        }
        // quality
        if (videoType != null) {
            formattedFilename = formattedFilename.replace("*type*", videoType);
        } else {
            formattedFilename = formattedFilename.replace("*type*", "");
        }
        // quality
        if (videoQuality != null) {
            formattedFilename = formattedFilename.replace("*quality*", videoQuality);
        } else {
            formattedFilename = formattedFilename.replace("*quality*", "");
        }
        // file extension
        if (videoExt != null) {
            formattedFilename = formattedFilename.replace("*ext*", videoExt);
        } else {
            formattedFilename = formattedFilename.replace("*ext*", ".mp4");
        }
        // Insert filename at the end to prevent errors with tags
        if (videoTitle != null) {
            formattedFilename = formattedFilename.replace("*videoname*", videoTitle);
        }
        // size
        formattedFilename = formattedFilename.replace("*videoFrameSize*", videoFrameSize);
        // bitrate
        formattedFilename = formattedFilename.replace("*videoBitrate*", videoBitrate);
        return formattedFilename;
    }

    public static SimpleDateFormat getFormatterForDate(final String dateSrc) {
        final SimpleDateFormat formatter;
        if (dateSrc.matches("\\d{4}\\-\\d{2}\\-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
            formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        } else if (dateSrc.matches("\\d{4}\\-\\d{2}\\-\\d{2}:\\d{2}:\\d{2}:\\d{2}")) {
            formatter = new SimpleDateFormat("yyyy-MM-dd:HH:mm:ss");
        } else {
            formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
        return formatter;
    }

    public static String getForcedReferer(final DownloadLink dl) {
        return dl.getStringProperty("vimeo_forced_referer", null);
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
        if (link != null) {
            link.removeProperty("directURL");
        }
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public String getDescription() {
        return "JDownloader's Vimeo Plugin helps downloading videoclips from vimeo.com. Vimeo provides different video qualities.";
    }

    private final static String defaultCustomFilename = "*videoname*_*quality*_*type**ext*";
    private final static String defaultCustomDate     = "dd.MM.yyyy";

    private void setConfigElements() {
        final ConfigEntry loadbest = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), Q_BEST, JDL.L("plugins.hoster.vimeo.best", "Returns a single <b>best</b> result per video url based on selection below.")).setDefaultValue(false);
        getConfig().addEntry(loadbest);
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), Q_ORIGINAL, JDL.L("plugins.hoster.vimeo.loadoriginal", "Load Original Version")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), Q_HD, JDL.L("plugins.hoster.vimeo.loadhd", "Load HD Version")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), Q_SD, JDL.L("plugins.hoster.vimeo.loadsd", "Load SD Version")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), Q_MOBILE, JDL.L("plugins.hoster.vimeo.loadmobile", "Load Mobile Version")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), SUBTITLE, JDL.L("plugins.hoster.vimeo.subtitle", "Load Subtitle")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), P_240, JDL.L("plugins.hoster.vimeo.240p", "240p")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), P_360, JDL.L("plugins.hoster.vimeo.360p", "360p")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), P_480, JDL.L("plugins.hoster.vimeo.480p", "480p")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), P_540, JDL.L("plugins.hoster.vimeo.540p", "540p")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), P_720, JDL.L("plugins.hoster.vimeo.720p", "720p")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), P_1080, JDL.L("plugins.hoster.vimeo.108p", "1080p")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), P_1440, JDL.L("plugins.hoster.vimeo.1440p", "1440p")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), P_2560, JDL.L("plugins.hoster.vimeo.2560p", "2560p")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ASK_REF, JDL.L("plugins.hoster.vimeo.askref", "Ask for referer")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Customise filename"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_DATE, JDL.L("plugins.hoster.vimeocom.customdate", "Define date:")).setDefaultValue(defaultCustomDate));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILENAME, JDL.L("plugins.hoster.vimeocom.customfilename", "Define filename:")).setDefaultValue(defaultCustomFilename));
        final StringBuilder sb = new StringBuilder();
        sb.append("Explanation of the available tags:\r\n");
        sb.append("*channelname* = name of the channel/uploader\r\n");
        sb.append("*date* = date when the video was posted - appears in the user-defined format above\r\n");
        sb.append("*videoname* = name of the video without extension\r\n");
        sb.append("*quality* = mobile or sd or hd\r\n");
        sb.append("*videoid* = id of the video\r\n");
        sb.append("*videoFrameSize* = size of video eg. 640x480 (not always available)\r\n");
        sb.append("*videoBitrate* = bitrate of video eg. xxxkbits (not always available)\r\n");
        sb.append("*type* = STREAM or DOWNLOAD\r\n");
        sb.append("*ext* = the extension of the file, in this case usually '.mp4'");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALWAYS_LOGIN, JDL.L("plugins.hoster.vimeo.alwayslogin", "Always login with account?")).setDefaultValue(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sb.toString()));
    }
}