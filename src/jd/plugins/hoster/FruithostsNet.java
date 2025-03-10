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

import org.jdownloader.plugins.components.FruithostedCDN;

import jd.PluginWrapper;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "fruithosts.net", "streamango.com" }, urls = { "https?://(?:www\\.)?fruithosts\\.net/(?:f|embed|e)/([a-z0-9]+)(/[a-zA-Z0-9_\\-]+)?", "https?://(?:www\\.)?streamangos?\\.com/(?:f|embed|e)/([a-z0-9]+)(/[a-zA-Z0-9_\\-]+)?" })
public class FruithostsNet extends FruithostedCDN {
    public FruithostsNet(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://fruithosts.net/register");
    }

    @Override
    public String getAPIBase() {
        return "https://api.fruithosted.net";
    }

    @Override
    public String getAGBLink() {
        return "http://fruithosts.net/tos";
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return true;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return true;
        }
    }

    public int getDownloadModeMaxChunks(final Account account, DownloadLink downloadlink) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return 0;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 0;
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultaneousFreeAccountDownloads() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }
}