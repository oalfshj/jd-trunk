//    jDownloader - Downloadmanager
//    Copyright (C) 2013  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Request;
import jd.nutils.encoding.Encoding;
import jd.nutils.encoding.HTMLEntities;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountRequiredException;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.SiteType.SiteTemplate;
import jd.utils.JDUtilities;

import org.appwork.utils.DebugMode;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.components.XFileSharingProBasic;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

@SuppressWarnings("deprecation")
@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = {}, urls = {})
public class GenericXFileShareProFolder extends antiDDoSForDecrypt {
    private static final String[] domains        = new String[] { "up-4.net", "up-4ever.com", "up-4ever.net", "subyshare.com", "brupload.net", "exclusivefaile.com", "exclusiveloader.com", "hulkload.com", "koofile.com", "powvideo.net", "lunaticfiles.com", "youwatch.org", "streamratio.com", "vshare.eu", "up.media1fire.com", "salefiles.com", "ortofiles.com", "restfile.ca", "restfilee.com", "storagely.com", "free-uploading.com", "rapidfileshare.net", "fireget.com", "ishareupload.com", "gorillavid.in", "mixshared.com", "longfiles.com", "novafile.com", "orangefiles.me", "qtyfiles.com", "free-uploading.com", "free-uploading.com", "uppit.com", "downloadani.me", "faststore.org", "clicknupload.org", "isra.cloud", "world-files.com", "katfile.com", "filefox.cc", "cosmobox.org", "easybytez.com" };
    /* This list contains all hosts which need special Patterns (see below) - most XFS hosts will have the same folder patterns! */
    private static final String[] specialDomains = { "usersfiles.com", "userscloud.com", "hotlink.cc", "ex-load.com", "imgbaron.com", "filespace.com", "spaceforfiles.com", "prefiles.com" };

    public static String[] getAnnotationNames() {
        return getAllDomains();
    }

    @Override
    public String[] siteSupportedNames() {
        return getAllDomains();
    }

    /* Returns Array containing all elements of domains + specialDomains. */
    public static String[] getAllDomains() {
        final List<String> ret = new ArrayList<String>();
        ret.addAll(Arrays.asList(domains));
        ret.addAll(Arrays.asList(specialDomains));
        return ret.toArray(new String[0]);
    }

    public static String[] getAnnotationUrls() {
        final List<String> ret = new ArrayList<String>();
        /* First add domains with normal patterns! */
        for (int i = 0; i < domains.length; i++) {
            ret.add("https?://(?:www\\.)?" + Pattern.quote(domains[i]) + "/(users/[a-z0-9_]+(?:/[^\\?\r\n]+)?|folder/\\d+/[^\\?\r\n]+)");
        }
        /*
         * Now add special patterns - this might be ugly but usually we do not get new specialDomains! Keep in mind that their patterns have
         * to be in order and the number of patterns has to be the same as the total number of domains!
         */
        /* userscloud.com & usersfiles.com */
        ret.add("https?://(?:www\\.)?usersfiles\\.com/go/[a-zA-Z0-9]{12}/?");
        ret.add("https?://(?:www\\.)?userscloud\\.com/go/[a-zA-Z0-9]{12}/?");
        /* hotlink.cc & ex-load.com */
        ret.add("https?://(?:www\\.)?hotlink\\.cc/folder/[a-f0-9\\-]+");
        ret.add("https?://(?:www\\.)?ex\\-load\\.com/folder/[a-f0-9\\-]+");
        /* imgbaron.com */
        ret.add("https?://(?:www\\.)?imgbaron\\.com/g/[A-Za-z0-9]+");
        /* filespace.com & spaceforfiles.com */
        ret.add("https?://filespace\\.com/dir/[a-z0-9]+");
        ret.add("https?://spaceforfiles\\.com/dir/[a-z0-9]+");
        /* prefiles.com */
        ret.add("https?://(?:www\\.)?prefiles\\.com/folder/\\d+[A-Za-z0-9\\-_]+");
        return ret.toArray(new String[0]);
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.SibSoft_XFileShare;
    }

    // DEV NOTES
    // other: keep last /.+ for fpName. Not needed otherwise.
    // other: group sister sites or aliased domains together, for easy
    // maintenance.
    private String                        host           = null;
    private String                        parameter      = null;
    private boolean                       fast_linkcheck = false;
    private final ArrayList<String>       dupe           = new ArrayList<String>();
    private final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
    FilePackage                           fp             = null;

    /**
     * @author raztoki
     */
    public GenericXFileShareProFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        dupe.clear();
        decryptedLinks.clear();
        postData = null;
        i = 1;
        parameter = param.toString();
        host = new Regex(parameter, "https?://(www\\.)?([^:/]+)").getMatch(1);
        if (host == null) {
            logger.warning("Failure finding HOST : " + parameter);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.setCookie("https://" + host, "lang", "english");
        br.setFollowRedirects(true);
        int counter = 0;
        final int maxCounter = 1;
        boolean loggedIN = false;
        do {
            try {
                getPage(parameter);
                if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("No such user exist|No such folder")) {
                    logger.info("Incorrect URL, Invalid user or empty folder");
                    decryptedLinks.add(this.createOfflinelink(parameter));
                    return decryptedLinks;
                } else if (br.containsHTML(">\\s*?Guest access not possible")) {
                    /* 2019-08-13: Rare special case E.g. easybytez.com */
                    if (loggedIN) {
                        logger.info("We are loggedIN but still cannot view this folder --> Wrong account or crawler plugin failure");
                        throw new AccountRequiredException("Folder not accessible with this account");
                    }
                    logger.info("Cannot access folder without login --> Trying to login and retry");
                    final PluginForHost hostPlg = JDUtilities.getPluginForHost(this.getHost());
                    if (!(hostPlg instanceof XFileSharingProBasic)) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    final Account acc = AccountController.getInstance().getValidAccount(hostPlg);
                    if (acc == null) {
                        throw new AccountRequiredException("Folder not accessible without account");
                    }
                    hostPlg.setBrowser(this.br);
                    try {
                        ((XFileSharingProBasic) hostPlg).loginWebsite(acc, false);
                        loggedIN = true;
                    } catch (Exception e) {
                        ((XFileSharingProBasic) hostPlg).handleAccountException(acc, logger, e);
                    }
                    continue;
                } else {
                    /* Folder should be accessible without account --> Step out of loop */
                    break;
                }
            } finally {
                counter++;
            }
        } while (counter <= maxCounter);
        /* name isn't needed, other than than text output for fpName. */
        final String username = new Regex(parameter, "/users/([^/]+)").getMatch(0);
        String fpName = new Regex(parameter, "(folder/\\d+/|f/[a-z0-9]+/|go/[a-z0-9]+/)[^/]+/(.+)").getMatch(1); // name
        if (fpName == null) {
            fpName = new Regex(parameter, "(folder/\\d+/|f/[a-z0-9]+/|go/[a-z0-9]+/)(.+)").getMatch(1); // id
            if (fpName == null) {
                fpName = new Regex(parameter, "users/[a-z0-9_]+/[^/]+/(.+)").getMatch(0); // name
                if (fpName == null) {
                    fpName = new Regex(parameter, "users/[a-z0-9_]+/(.+)").getMatch(0); // id
                    if (fpName == null) {
                        if (parameter.matches(".+users(?:files|cloud)\\.com/.+")) {
                            fpName = br.getRegex("<title>\\s*(.*?)\\s*folder\\s*</title>").getMatch(0);
                        } else if ("hotlink.cc".equals(host)) {
                            fpName = br.getRegex("<i class=\"glyphicon glyphicon-folder-open\"></i>\\s*(.*?)\\s*</span>").getMatch(0);
                        } else if ("ex-load.com".equals(host)) {
                            fpName = br.getRegex("Files in (.*?) folder</title>").getMatch(0);
                            if (fpName == null) {
                                fpName = br.getRegex("<h1.*?</i>\\s*(.*?)\\s*</h1>").getMatch(0);
                            }
                        }
                    }
                }
            }
            if (fpName == null) {
                /* 2019-02-08: E.g. for photo galleries (e.g. imgbaron.com) */
                fpName = br.getRegex("<H1>\\s*?(.*?)\\s*?</H1>").getMatch(0);
            }
        }
        if (fpName == null) {
            /* Final fallback */
            fpName = username;
        }
        if (fpName != null) {
            fpName = Encoding.htmlDecode(fpName);
            fp = FilePackage.getInstance();
            fp.setName(fpName.trim());
        }
        dupe.add(parameter);
        // count value prevents continuous loop.
        int count = 0;
        do {
            count = decryptedLinks.size();
            parsePage();
        } while (!this.isAbort() && decryptedLinks.size() > count && parseNextPage());
        return decryptedLinks;
    }

    private void parsePage() throws PluginException {
        final String[] links = br.getRegex("href=(\"|')(https?://(?:www\\.)?" + Pattern.quote(host) + "/[a-z0-9]{12}(?:/.*?)?)\\1").getColumn(1);
        if (links != null && links.length > 0) {
            for (final String link : links) {
                final String linkid = new Regex(link, Pattern.compile("https?://[^/]+/([a-z0-9]{12})", Pattern.CASE_INSENSITIVE)).getMatch(0);
                if (!dupe.contains(linkid)) {
                    /**
                     * TODO: Consider adding support for "fast linkcheck" option via XFS core (superclass) --> Set links as available here -
                     * maybe only if filename is given inside URL (which is often the case). In general, files inside a folder should be
                     * online!
                     */
                    final DownloadLink dl = createDownloadlink(link);
                    /* Works for e.g. world-files.com, brupload.net */
                    /* TODO: Improve this RegEx e.g. for katfile.com, brupload.net */
                    String html_snippet = new Regex(br.toString(), "<tr>\\s*<td>\\s*<a[^<]*" + linkid + ".*?</td>\\s*</tr>").getMatch(-1);
                    if (StringUtils.isEmpty(html_snippet)) {
                        /* E.g. up-4.net */
                        /* TODO: Improve this RegEx */
                        html_snippet = new Regex(br.toString(), "<div class=\"file\\-details\">\\s+<h3 class=\"file\\-ttl\"><a href=\"[^\"]+/" + linkid + ".*?</div>\\s+</div>").getMatch(-1);
                    }
                    /* Set ContentURL - VERY important for XFS (Mass-)Linkchecking! */
                    dl.setContentUrl(link);
                    String url_filename = new Regex(link, "[a-z0-9]{12}/(.+)\\.html$").getMatch(0);
                    /* E.g. up-4.net */
                    String html_filename = null;
                    String html_filesize = null;
                    if (html_snippet != null) {
                        html_filename = new Regex(html_snippet, "target=\"_blank\">\\s*([^<>\"]+)\\s*</(a|td)>").getMatch(0);
                        html_filesize = new Regex(html_snippet, "([\\d\\.]+ (?:B|KB|MB|GB))").getMatch(0);
                    }
                    String filename;
                    if (html_filename != null) {
                        filename = html_filename;
                    } else {
                        filename = url_filename;
                    }
                    if (!StringUtils.isEmpty(filename)) {
                        if (filename.endsWith("&#133;")) {
                            /*
                             * Indicates that this is not the complete filename but there is nothing we can do at this stage - full
                             * filenames should be displayed once a full linkcheck is performed or at least once a download starts.
                             */
                            filename = filename.replace("&#133;", "");
                        }
                        dl.setName(filename);
                    }
                    /*
                     * TODO: Maybe set all URLs as offline for which filename + filesize are given. Not sure whether a folder can only
                     * contain available files / whether dead files get removed from folders automatically ...
                     */
                    if (!StringUtils.isEmpty(html_filesize)) {
                        dl.setDownloadSize(SizeFormatter.getSize(html_filesize));
                    }
                    if (fast_linkcheck || DebugMode.TRUE_IN_IDE_ELSE_FALSE) {
                        dl.setAvailable(true);
                    }
                    decryptedLinks.add(dl);
                    distribute(dl);
                    dupe.add(linkid);
                }
            }
        }
        // these should only be shown when its a /user/ decrypt task
        final String cleanedUpAddedFolderLink = new Regex(parameter, "https?://[^/]+/(.+)").getMatch(0);
        final String folders[] = br.getRegex("folder.?\\.gif.*?<a href=\"(.+?" + Pattern.quote(host) + "[^\"]+users/[^\"]+)").getColumn(0);
        if (folders != null && folders.length > 0) {
            for (final String folderlink : folders) {
                final String cleanedUpFoundFolderLink = new Regex(folderlink, "https?://[^/]+/(.+)").getMatch(0);
                /* Make sure that we're not grabbing the parent folder but only the folder that the user has added + eventual subfolders! */
                final boolean folderIsChildFolder = cleanedUpFoundFolderLink.length() > cleanedUpAddedFolderLink.length();
                if (folderlink.matches(this.getSupportedLinks().pattern()) && !dupe.contains(folderlink) && folderIsChildFolder) {
                    final DownloadLink dlfolder = createDownloadlink(folderlink);
                    decryptedLinks.add(dlfolder);
                    distribute(dlfolder);
                    dupe.add(folderlink);
                }
            }
        }
    }

    private int    i        = 1;
    private String postData = null;

    private boolean parseNextPage() throws Exception {
        // not sure if this is the same for normal folders, but the following
        // picks up users/username/*, 2019-02-08: will also work for photo galleries ('host.tld/g/bla')
        /* Increment page */
        i++;
        String nextPage = br.getRegex("<div class=(\"|')paging\\1>[^\r\n]+<a href=('|\")([^']+\\&amp;page=\\d+|/go/[a-zA-Z0-9]{12}/\\d+/?)\\2>Next").getMatch(2);
        if (nextPage != null) {
            nextPage = HTMLEntities.unhtmlentities(nextPage);
            nextPage = Request.getLocation(nextPage, br.getRequest());
            getPage(nextPage);
            return true;
        }
        if (postData == null) {
            /* Pagination ? */
            final String pagination = br.getRegex("setPagination\\('\\.files_paging',.*?\\);").getMatch(-1);
            if (pagination == null) {
                return false;
            }
            final String op = new Regex(pagination, "op:\\s*'(\\w+)'").getMatch(0);
            final String usr_login = new Regex(pagination, "usr_login:\\s*'(\\w+)'").getMatch(0);
            String fld_id = new Regex(pagination, "fld_id:\\s*'(\\w+)'").getMatch(0);
            if ("user_public".equalsIgnoreCase(op) && fld_id == null) {
                /* Decrypt all files of a user --> No folder_id given/required! Example: up-4-net */
                fld_id = "";
            }
            if (op == null || usr_login == null || fld_id == null) {
                return false;
            }
            postData = "op=" + Encoding.urlEncode(op) + "&load=files&page=%s&fld_id=" + Encoding.urlEncode(fld_id) + "&usr_login=" + Encoding.urlEncode(usr_login);
        }
        br.getHeaders().put("Accept", "*/*");
        br.getHeaders().put("x-requested-with", "XMLHttpRequest");
        postPage(br.getURL(), String.format(postData, i));
        return true;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}