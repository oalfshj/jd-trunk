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
package jd.plugins.hoster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForHost;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.components.MultiHosterManagement;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.SiteType.SiteTemplate;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "megarapido.net" }, urls = { "" })
public class MegarapidoNet extends antiDDoSForHost {
    /* Tags: conexaomega.com.br, megarapido.net, superdown.com.br */
    private final String                 PRIMARYURL                   = "https://" + this.getHost();
    private final String                 NICE_HOSTproperty            = this.getHost().replaceAll("(\\.|-)", "") + "_";
    private final String                 DIRECTLINK                   = NICE_HOSTproperty + "DIRECTLINK";
    /* Connection limits */
    private static final boolean         ACCOUNT_PREMIUM_RESUME       = true;
    private static final int             ACCOUNT_PREMIUM_MAXCHUNKS    = 0;
    private static final int             ACCOUNT_PREMIUM_MAXDOWNLOADS = 20;
    private static final String          default_UA                   = "JDownloader";
    private int                          statuscode                   = 0;
    private static MultiHosterManagement mhm                          = new MultiHosterManagement("megarapido.net");

    public MegarapidoNet(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://megarapido.net/planos");
    }

    @Override
    public String getAGBLink() {
        return "https://megarapido.net/termos-e-condicoes";
    }

    private Browser newBrowser() {
        br = new Browser();
        br.setCookiesExclusive(true);
        br.setFollowRedirects(true);
        br.getHeaders().put("User-Agent", default_UA);
        br.setCustomCharset("utf-8");
        br.setConnectTimeout(60 * 1000);
        br.setReadTimeout(60 * 1000);
        return br;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public boolean canHandle(DownloadLink downloadLink, Account account) throws Exception {
        if (account == null) {
            /* without account its not possible to download the link */
            return false;
        }
        return true;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        /* handle premium should never be called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        mhm.runCheck(account, link);
        login(account);
        String dllink = checkDirectLink(link, DIRECTLINK);
        if (dllink == null) {
            final Form form = new Form();
            form.setAction("/api/generator/generate");
            form.put("link", Encoding.urlEncode(link.getPluginPatternMatcher()));
            formAPISafe(form, link, account);
            dllink = PluginJSonUtils.getJson(br, "link");
            if (dllink == null || dllink.length() > 500) {
                /* Should never happen */
                mhm.handleErrorGeneric(account, link, "dllinknull", 5);
            }
        }
        handleDL(account, link, dllink);
    }

    @SuppressWarnings("deprecation")
    private void handleDL(final Account account, final DownloadLink link, final String dllink) throws Exception {
        link.setProperty(DIRECTLINK, dllink);
        try {
            dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, ACCOUNT_PREMIUM_RESUME, ACCOUNT_PREMIUM_MAXCHUNKS);
            if (dl.getConnection().getResponseCode() == 416) {
                logger.info("Resume impossible ...");
                link.setChunksProgress(null);
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            final String contenttype = dl.getConnection().getContentType();
            if (contenttype.contains("html")) {
                if (dl.getConnection().getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
                }
                br.followConnection();
                updatestatuscode();
                handleAPIErrors(account, link);
                mhm.handleErrorGeneric(account, link, "unknowndlerror", 5);
            }
            dl.startDownload();
        } catch (final Exception e) {
            link.setProperty(DIRECTLINK, Property.NULL);
            throw e;
        }
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openHeadConnection(dllink);
                if (con.getContentType().contains("text") || !con.isOK() || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                logger.log(e);
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }
        return dllink;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account);
        final String date = PluginJSonUtils.getJson(br, "data_final_premium");
        if (StringUtils.isEmpty(date)) {
            /* 2019-08-22: This should always be given */
            logger.warning("Expiredate is missing");
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "Failed to find expiredate", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
        }
        ai.setValidUntil(TimeFormatter.getMilliSeconds(date, "yyyy-MM-dd hh:mm:ss", Locale.ENGLISH), br);
        if (ai.isExpired()) {
            // free accounts can download, tested with zippyshare link - 20170706-raz
            ai.setExpired(false);
            // /* Prevent downloads via free account - they have no traffic! */
            // ai.setTrafficLeft(0);
            // throw new AccountInvalidException("Unsupported account type");
            ai.setUnlimitedTraffic();
            ai.setStatus("Free Account");
            account.setType(AccountType.FREE);
            account.setAccountInfo(ai);
        } else {
            ai.setUnlimitedTraffic();
            ai.setStatus("Premium Account");
            account.setType(AccountType.PREMIUM);
        }
        // host map found here
        getPage("/api/servers/list");
        // invalid json response
        final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject("{ \"hostarray\":" + br.toString() + "}");
        final ArrayList<LinkedHashMap<String, Object>> hostDomainsInfo = (ArrayList) entries.get("hostarray");
        final ArrayList<String> supportedHosts = new ArrayList<String>();
        for (final LinkedHashMap<String, Object> entry : hostDomainsInfo) {
            // nome=name
            final String crippledhost = ((String) entry.get("nome")).toLowerCase(Locale.ENGLISH);
            final String status = (String) entry.get("status");
            // Disponível = Available
            // Em Testes = In Tests
            // Em Manutenção = Under maintenance
            if (!"Disponível".equals(status)) {
                continue;
            }
            supportedHosts.add(crippledhost);
            // wont use, just use error message.
            // final String limit = (String)entries.get("limit");
            // //Ilimitado = unlimited
        }
        ai.setMultiHostSupport(this, supportedHosts);
        return ai;
    }

    private void login(final Account account) throws Exception {
        synchronized (account) {
            try {
                br = newBrowser();
                final Cookies cookies = account.loadCookies("");
                boolean loggedIN = false;
                if (cookies != null) {
                    logger.info("Trying to login via cookies");
                    br.setCookies(PRIMARYURL, cookies);
                    br.getPage(PRIMARYURL + "/api/login/signed_in");
                    // should have json...
                    loggedIN = !isCookiesSessionInvalid();
                }
                if (!loggedIN) {
                    logger.info("Performing full login");
                    getAPISafe(PRIMARYURL + "/login", null, account);
                    final Form f = new Form();
                    f.setAction("/api/login/sign_in");
                    f.put("email", Encoding.urlEncode(account.getUser()));
                    f.put("password", Encoding.urlEncode(account.getPass()));
                    final DownloadLink dummyLink = new DownloadLink(this, "Account", this.getHost(), this.getHost(), true);
                    this.setDownloadLink(dummyLink);
                    final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br, "6Lel6CQUAAAAANRfiz7Kh8rdyzHgh4An39DbHb67").getToken();
                    f.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                    formAPISafe(f, null, account);
                    if ("Email ou senha inválidos".equals(br.toString())) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                account.saveCookies(br.getCookies(PRIMARYURL), "");
            } catch (final PluginException e) {
                e.printStackTrace();
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    private boolean isCookiesSessionInvalid() {
        final boolean result = br.getCookie(this.getHost(), "key") == null || "deleted".equals(br.getCookie(this.getHost(), "key")) || (br.getHttpConnection().getResponseCode() == 401 && "Usuário deslogado".equals(br.toString()));
        return result;
    }

    private void getAPISafe(final String accesslink, final DownloadLink link, final Account account) throws Exception {
        getPage(accesslink);
        updatestatuscode();
        handleAPIErrors(account, link);
    }

    private void postAPISafe(final String accesslink, final String postdata, final DownloadLink link, final Account account) throws Exception {
        postPage(accesslink, postdata);
        updatestatuscode();
        handleAPIErrors(account, link);
    }

    private void formAPISafe(final Form form, final DownloadLink link, final Account account) throws Exception {
        submitForm(form);
        updatestatuscode();
        handleAPIErrors(account, link);
    }

    /**
     * 0 = everything ok, 1-99 = "error"-errors
     */
    private void updatestatuscode() {
        String error = br.getRegex("class=alert-message error > ([^<>]*?)</div>").getMatch(0);
        if (error == null) {
            /* 2019-08-22: Website may respond with only an errormessage as plaintext, no json or html code at all */
            error = br.toString();
        }
        if (error != null) {
            if (error.contains("Desculpe-nos, no momento o servidor")) {
                statuscode = 1;
            } else if (error.contains("Seu gerador premium está zerado, por favor, compre um de nossos planos")) {
                statuscode = 2;
            }
        } else {
            statuscode = 0;
        }
    }

    private void handleAPIErrors(final Account account, final DownloadLink link) throws PluginException, InterruptedException {
        switch (statuscode) {
        case 0:
            /* Everything ok */
            break;
        case 1:
            /* Host currently not supported --> deactivate it for some hours. */
            mhm.putError(account, link, 3 * 60 * 1000l, "Host is currently not supported");
        case 2:
            /* 2019-08-22: (Free-Account)Traffic empty (??) */
            throw new PluginException(LinkStatus.ERROR_PREMIUM, "No (Free-)Account traffic available anymore", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
        default:
            break;
        }
    }

    @Override
    public int getMaxSimultanDownload(final DownloadLink link, final Account account) {
        return ACCOUNT_PREMIUM_MAXDOWNLOADS;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.Unknown_MultihosterScript;
    }
}