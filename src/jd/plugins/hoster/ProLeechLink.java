package jd.plugins.hoster;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.plugins.components.antiDDoSForHost;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.http.Cookies;
import jd.http.requests.PostRequest;
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

@HostPlugin(revision = "$Revision: 39245 $", interfaceVersion = 3, names = { "proleech.link" }, urls = { "https?://proleech\\.link/download/[a-zA-Z0-9]+(/.*)?" })
public class ProLeechLink extends antiDDoSForHost {
    public ProLeechLink(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://proleech.link/signup");
        setConfigElements();
    }

    private static MultiHosterManagement mhm = new MultiHosterManagement("proleech.link");

    @Override
    public String getAGBLink() {
        return "https://proleech.link/page/terms";
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink parameter) throws Exception {
        final String fileName = new Regex(parameter.getPluginPatternMatcher(), "download/[a-zA-Z0-9]+/([^/\\?]+)").getMatch(0);
        if (fileName != null && !parameter.isNameSet()) {
            parameter.setName(fileName);
        }
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, ai);
        /* Contains all filehosts available for free account users regardless of their status */
        String[] filehosts_free = null;
        /* Contains all filehosts available for premium users and listed as online/working */
        String[] filehosts_premium_online = null;
        List<String> filehosts_premium_onlineArray = null;
        /* Contains all filehosts available for premium users and listed as online/working */
        List<String> filehosts_free_onlineArray = new ArrayList<String>();
        {
            /* Grab free hosts */
            if (br.getURL() == null || !br.getURL().contains("/downloader")) {
                this.getPage("/downloader");
            }
            final String html_free_filehosts = br.getRegex("<section id=\"content\">.*?Free Filehosters</div>.*?</section>").getMatch(-1);
            filehosts_free = new Regex(html_free_filehosts, "domain=([^\"]+)").getColumn(0);
        }
        {
            /* Grab premium hosts */
            getPage("/page/hostlist");
            filehosts_premium_online = br.getRegex("<td>\\s*\\d+\\s*</td>\\s*<td>\\s*<img[^<]+/?>\\s*([^<]*?)\\s*</td>\\s*<td>\\s*<span\\s*class\\s*=\\s*\"label\\s*label-success\"\\s*>\\s*Online").getColumn(0);
            if (filehosts_premium_online == null || filehosts_premium_online.length == 0) {
                logger.warning("Failed to find list of supported hosts");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            filehosts_premium_onlineArray = Arrays.asList(filehosts_premium_online);
        }
        /* Set supported hosts depending on account type */
        if (account.getType() == AccountType.PREMIUM && !ai.isExpired()) {
            /* Premium account - bigger list of supported hosts */
            ai.setMultiHostSupport(this, filehosts_premium_onlineArray);
        } else {
            /* Free & Expired[=Free] accounts - they support much less hosts */
            if (filehosts_free != null && filehosts_free.length != 0) {
                /*
                 * We only see the online status of each host in the big list. Now we have to find out which of all free filehosts are
                 * currently online/supported.
                 */
                for (final String host : filehosts_free) {
                    if (filehosts_premium_onlineArray.contains(host)) {
                        /* Filehost should be supported/online --> Add to the list we will later set */
                        filehosts_free_onlineArray.add(host);
                    }
                }
                ai.setMultiHostSupport(this, filehosts_free_onlineArray);
            }
        }
        return ai;
    }

    private boolean checkCookies(Cookies cookies) throws PluginException {
        if (cookies == null || cookies.get("amember_nr", Cookies.NOTDELETEDPATTERN) == null || cookies.get("amember_ru", Cookies.NOTDELETEDPATTERN) == null || cookies.get("amember_rp", Cookies.NOTDELETEDPATTERN) == null) {
            return false;
        } else {
            return true;
        }
    }

    private void login(Account account, AccountInfo ai) throws Exception {
        synchronized (account) {
            try {
                Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    br.setCookies(getHost(), cookies);
                    getPage("https://proleech.link/member");
                    br.followRedirect();
                    cookies = br.getCookies(getHost());
                    if (br.getFormbyAction("/login") != null) {
                        cookies = null;
                    }
                }
                if (!checkCookies(cookies)) {
                    br.clearCookies(getHost());
                    getPage("https://proleech.link");
                    getPage("https://proleech.link/login/index");
                    final Form login = br.getFormbyAction("/login");
                    if (login == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    login.put("amember_login", URLEncoder.encode(account.getUser(), "UTF-8"));
                    login.put("amember_pass", URLEncoder.encode(account.getPass(), "UTF-8"));
                    submitForm(login);
                    br.followRedirect();
                    if (br.getFormbyAction("/login") != null) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        getPage("/member");
                        br.followRedirect();
                        if (br.getFormbyAction("/login") != null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        cookies = br.getCookies(getHost());
                    }
                }
                if (!checkCookies(cookies)) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                final String activeSubscription = br.getRegex("am-list-subscriptions\">\\s*<li[^<]*>(.*?)</li>").getMatch(0);
                String accountStatus = null;
                if (activeSubscription != null) {
                    final String expireDate = new Regex(activeSubscription, "([a-zA-Z]+\\s*\\d+,\\s*\\d{4})").getMatch(0);
                    if (expireDate != null) {
                        final long validUntil = TimeFormatter.getMilliSeconds(expireDate, "MMM' 'dd', 'yyyy", Locale.ENGLISH);
                        if (ai != null) {
                            ai.setValidUntil(validUntil);
                        }
                        account.setType(AccountType.PREMIUM);
                        accountStatus = "Premium user";
                        account.setConcurrentUsePossible(true);
                        account.setMaxSimultanDownloads(-1);
                    }
                } else {
                    account.setType(AccountType.FREE);
                    accountStatus = "Free user";
                    account.setConcurrentUsePossible(true);
                    if (ai != null) {
                        getPage("/downloader");
                        int maxfiles_per_day_used = 0;
                        int maxfiles_per_day_maxvalue = 0;
                        final Regex maxfiles_per_day = br.getRegex("<li>Files per day:\\s*?<b>\\s*?(\\d+)?\\s*?/\\s*?(\\d+)\\s*?</li>");
                        final String maxfiles_per_day_usedStr = maxfiles_per_day.getMatch(0);
                        final String maxfiles_per_day_maxvalueStr = maxfiles_per_day.getMatch(1);
                        final String max_free_filesize = br.getRegex("<li>Max\\. Filesize: <b>(\\d+ [^<>\"]+)</li>").getMatch(0);
                        if (maxfiles_per_day_usedStr != null) {
                            maxfiles_per_day_used = Integer.parseInt(maxfiles_per_day_usedStr);
                        }
                        if (maxfiles_per_day_maxvalueStr != null) {
                            maxfiles_per_day_maxvalue = Integer.parseInt(maxfiles_per_day_maxvalueStr);
                        }
                        /* ok = "/2" or "1/2", not ok = "2/2" */
                        if (max_free_filesize != null && maxfiles_per_day_maxvalue > 0) {
                            final int files_this_day_remaining = maxfiles_per_day_maxvalue - maxfiles_per_day_used;
                            if (files_this_day_remaining > 0) {
                                ai.setTrafficLeft(SizeFormatter.getSize(max_free_filesize));
                                /* 2019-08-14: Max files per day = 2 so a limit of 1 should be good! */
                                account.setMaxSimultanDownloads(1);
                            } else {
                                /*
                                 * No links remaining --> Display account with ZERO traffic left as it cannot be used to download at the
                                 * moment!
                                 */
                                logger.info("Cannot use free account because the max number of dail downloads has already been reached");
                                ai.setTrafficLeft(0);
                                account.setMaxSimultanDownloads(0);
                            }
                            accountStatus += " [" + files_this_day_remaining + "/" + maxfiles_per_day_maxvalueStr + " daily downloads remaining]";
                        } else {
                            logger.info("Cannot use free account because we failed to find any information about the current account limits");
                            account.setMaxSimultanDownloads(0);
                            ai.setTrafficLeft(0);
                            accountStatus += " [No downloads possible]";
                        }
                    }
                }
                if (ai != null) {
                    ai.setStatus(accountStatus);
                }
                account.saveCookies(cookies, "");
            } catch (PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        mhm.runCheck(account, link);
        login(account, null);
        final String generatedDownloadURL = link.getStringProperty(getHost(), null);
        String downloadURL = null;
        if (generatedDownloadURL != null) {
            logger.info("Trying to re-use old generated downloadlink");
            try {
                dl = jd.plugins.BrowserAdapter.openDownload(br, link, generatedDownloadURL, true, 0);
                if (!dl.getConnection().isContentDisposition()) {
                    logger.info("Saved downloadurl did not work");
                    try {
                        br.followConnection();
                    } catch (final IOException e) {
                        logger.log(e);
                    }
                } else {
                    downloadURL = generatedDownloadURL;
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                logger.log(e);
            }
        }
        if (downloadURL == null) {
            logger.info("Trying to generate new downloadlink");
            final long userDefinedWaitHours = this.getPluginConfig().getLongProperty("DOWNLOADLINK_GENERATION_LIMIT", 0);
            final long timestamp_next_downloadlink_generation_allowed = link.getLongProperty("PROLEECH_TIMESTAMP_LAST_SUCCESSFUL_DOWNLOADLINK_CREATION", 0) + (userDefinedWaitHours * 60 * 60 * 1000);
            if (userDefinedWaitHours > 0 && timestamp_next_downloadlink_generation_allowed > System.currentTimeMillis()) {
                final long waittime_until_next_downloadlink_generation_is_allowed = timestamp_next_downloadlink_generation_allowed - System.currentTimeMillis();
                final String waittime_until_next_downloadlink_generation_is_allowed_Str = TimeFormatter.formatSeconds(waittime_until_next_downloadlink_generation_is_allowed / 1000, 0);
                logger.info("Next downloadlink generation is allowed in: " + waittime_until_next_downloadlink_generation_is_allowed_Str);
                /*
                 * 2019-08-14: Set a small waittime here so links can be tried earlier again - so not set the long waittime
                 * waittime_until_next_downloadlink_generation_is_allowed!
                 */
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Next downloadlink generation is allowed in " + waittime_until_next_downloadlink_generation_is_allowed_Str, 5 * 60 * 1000l);
            }
            final PostRequest post = new PostRequest("https://proleech.link/dl/debrid/deb_process.php");
            post.setContentType("application/x-www-form-urlencoded; charset=UTF-8");
            post.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            final String url = link.getDefaultPlugin().buildExternalDownloadURL(link, this);
            post.put("urllist", URLEncoder.encode(url, "UTF-8"));
            final String pass = link.getDownloadPassword();
            if (StringUtils.isEmpty(pass)) {
                post.put("pass", "");
            } else {
                post.put("pass", URLEncoder.encode(pass, "UTF-8"));
            }
            post.put("boxlinklist", "0");
            sendRequest(post);
            downloadURL = br.getRegex("class=\"[^\"]*success\".*?<a href\\s*=\\s*\"(https?://.*?)\"").getMatch(0);
            if (downloadURL == null) {
                final String danger = br.getRegex("class=\"[^\"]*danger\".*?<b>\\s*(.*?)\\s*</b>").getMatch(0);
                if (danger != null) {
                    logger.info("Found errormessage on website:");
                    logger.info(danger);
                }
                if (br.containsHTML(">\\s*?No link entered\\.?\\s*<")) {
                    mhm.handleErrorGeneric(account, link, "no_link_entered", 50, 2 * 60 * 1000l);
                } else if (br.containsHTML(">\\s*?Error getting the link from this account\\.?\\s*<")) {
                    mhm.putError(account, link, 2 * 60 * 1000l, "Error getting the link from this account");
                } else if (br.containsHTML(">\\s*?Our account has reached traffic limit\\.?\\s*<")) {
                    mhm.putError(account, link, 2 * 60 * 1000l, "Error getting the link from this account");
                } else if (br.containsHTML(">\\s*?This filehost is only enabled in")) {
                    mhm.putError(account, link, 10 * 60 * 1000l, "This filehost is only available in premium mode");
                } else if (br.containsHTML(">\\s*?You can only generate this link during Happy Hours")) {
                    /* 2019-08-15: Can happen in free account mode - no idea when this "Happy Hour" is. Tested with uptobox.com URLs. */
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "You can only generate this link during Happy Hours", 5 * 60 * 1000l);
                }
                mhm.handleErrorGeneric(account, link, "dllinknull", 50, 2 * 60 * 1000l);
            }
            link.setProperty("PROLEECH_TIMESTAMP_LAST_SUCCESSFUL_DOWNLOADLINK_CREATION", System.currentTimeMillis());
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, downloadURL, true, 0);
            final boolean isOkay = dl.getConnection().isOK() && (dl.getConnection().isContentDisposition() || StringUtils.containsIgnoreCase(dl.getConnection().getContentType(), "application/force-download"));
            if (!isOkay) {
                try {
                    br.followConnection();
                } catch (final IOException e) {
                    logger.log(e);
                }
                mhm.handleErrorGeneric(account, link, "unknowndlerror", 50, 2 * 60 * 1000l);
            }
        }
        link.setProperty(getHost(), downloadURL);
        dl.startDownload();
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        login(account, null);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, link.getPluginPatternMatcher(), true, 0);
        final boolean isOkay = dl.getConnection().isOK() && (dl.getConnection().isContentDisposition() || StringUtils.containsIgnoreCase(dl.getConnection().getContentType(), "application/force-download"));
        if (!isOkay) {
            try {
                br.followConnection();
            } catch (final IOException e) {
                logger.log(e);
            }
            if (StringUtils.endsWithCaseInsensitive(br.getURL(), "/downloader")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            } else {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error");
            }
        }
        dl.startDownload();
    }

    private void setConfigElements() {
        /* Crawler settings */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, getPluginConfig(), "DOWNLOADLINK_GENERATION_LIMIT", "Allow new downloadlink generation every X hours (default = 0 = unlimited/disabled)\r\nThis can save traffic but this can also slow down the download process", 0, 72, 1).setDefaultValue(0));
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handleFree(DownloadLink link) throws Exception {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
