//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.RandomUserAgent;
import jd.parser.html.HTMLParser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "paste2.org" }, urls = { "https?://(www\\.)?paste2\\.org/[A-Za-z0-9]+" })
public class Paste2Org extends PluginForDecrypt {
    public Paste2Org(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    // Tags: pastebin
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getHeaders().put("User-Agent", RandomUserAgent.generate());
        br.getPage(parameter);
        /* Error handling */
        if (br.getHttpConnection().getResponseCode() == 404) {
            logger.info("Link offline: " + parameter);
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        final String plaintxt = br.getRegex("<ol class='highlight code'>(.*?)</div></li></ol>").getMatch(0);
        if (plaintxt == null) {
            logger.info("Paste2 Decrypter: Could not find textfield: " + parameter);
            logger.info("Paste2 Decrypter: Please report this to JDownloader' Development Team.");
            return null;
        }
        final String[] links = HTMLParser.getHttpLinks(plaintxt, "");
        if (links == null || links.length == 0) {
            logger.info("Found no hosterlinks in plaintext from link " + parameter);
            try {
                decryptedLinks.add(this.createOfflinelink(parameter));
            } catch (final Throwable e) {
                /* Not available in old 0.9.581 Stable */
            }
            return decryptedLinks;
        }
        /* avoid recursion */
        for (int i = 0; i < links.length; i++) {
            String dlLink = links[i];
            if (!this.canHandle(dlLink)) {
                decryptedLinks.add(createDownloadlink(dlLink));
            }
        }
        return decryptedLinks;
    }
}