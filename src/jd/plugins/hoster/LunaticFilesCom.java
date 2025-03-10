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
import java.util.List;

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.components.XFileSharingProBasic;

import jd.PluginWrapper;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class LunaticFilesCom extends XFileSharingProBasic {
    public LunaticFilesCom(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(super.getPurchasePremiumURL());
    }

    /**
     * DEV NOTES XfileSharingProBasic Version SEE SUPER-CLASS<br />
     * mods: See overridden functions<br />
     * limit-info:<br />
     * captchatype-info: 2019-08-28: reCaptchaV2<br />
     * other:<br />
     */
    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        return XFileSharingProBasic.buildAnnotationUrls(getPluginDomains());
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "lunaticfiles.com" });
        return ret;
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return false;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return false;
        }
    }

    @Override
    public int getMaxChunks(final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return 1;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 1;
        }
    }

    @Override
    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return 1;
    }

    @Override
    public int getMaxSimultaneousFreeAccountDownloads() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    protected void checkErrors(final DownloadLink link, final Account account, final boolean checkAll) throws NumberFormatException, PluginException {
        /* 2019-07-04: Special: Some errormessages are only available in polish */
        super.checkErrors(link, account, checkAll);
        if (new Regex(correctedBR, "Przepraszamy, niestety akceptujemy tylko pobrania z Polski|We are sorry, we allow downloads only from Poland").matches()) {
            /* 2016-06-09: Special errorhandling - this message will usually appear after entering the (correct) captcha in free mode! */
            throw new PluginException(LinkStatus.ERROR_FATAL, "Download from this host are only possible via polish IP (or premium account)");
        } else if (correctedBR.contains(">Pobierasz już jeden plik z naszych serwerów")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Server error 'You're using all download slots for IP'", 10 * 60 * 1001l);
        }
    }

    @Override
    protected String regExTrafficLeft() {
        /* 2019-07-05: Special */
        /* Do NOT change this - they put this in the html code especially for download managers! to let them parse it */
        String availabletraffic = this.br.getRegex("TRAFFIC_LEFT (\\d+(?:\\.\\d{1,2})? (?:KB|MB|GB)) TRAFFIC_LEFT").getMatch(0);
        /* This traffic only gets used if the other traffic reaches 0 but let's sum them together to display the real traffic left. */
        String availabletraffic_extra = this.br.getRegex("TRAFFIC_LEFT_ADDITIONAL (\\d+(?:\\.\\d{1,2})? (?:KB|MB|GB)) TRAFFIC_LEFT_ADDITIONAL").getMatch(0);
        if (availabletraffic_extra == null) {
            /* Fallback RegEx - this should NOT be needed! */
            availabletraffic_extra = new Regex(correctedBR, "Pozostały transfer nieodnawialny:.*?<b>(\\d+(\\.\\d{1,2})? (?:KB|MB|GB))</b>").getMatch(0);
        }
        long trafficleft = 0;
        if (availabletraffic != null) {
            trafficleft += SizeFormatter.getSize(availabletraffic);
        }
        if (availabletraffic_extra != null) {
            trafficleft += SizeFormatter.getSize(availabletraffic_extra);
        }
        /*
         * We're formatting it back to a String which is not the best solution but we only need to do this because they have two trafficleft
         * values - XFS template might gets upgraded in the future for better handling of this case!
         */
        return SizeFormatter.formatBytes(trafficleft);
    }

    @Override
    protected Form findFormF1() {
        Form dlForm = null;
        /* First try to find Form for video hosts with multiple qualities. */
        final Form[] forms = br.getForms();
        for (final Form form : forms) {
            final InputField op_field = form.getInputFieldByName("op");
            /* E.g. name="op" value="download_orig" */
            if (form.containsHTML("method_") && op_field != null && op_field.getValue().contains("download2")) {
                dlForm = form;
                break;
            }
        }
        if (dlForm == null) {
            /* Fallback to template handling */
            dlForm = super.findFormF1();
        }
        return dlForm;
    }
}