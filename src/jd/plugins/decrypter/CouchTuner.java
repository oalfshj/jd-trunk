//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.util.ArrayList;

import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

@DecrypterPlugin(revision = "$Revision: 41172 $", interfaceVersion = 2, names = { "couchtuner.cloud" }, urls = { "https?://(www\\.)?(?:watch-online\\.xyz|couchtuner\\.(?:cloud|click|website)|2mycouchtuner\\.me|mycouchtuner\\.li)/.*" })
public class CouchTuner extends antiDDoSForDecrypt {
    public CouchTuner(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setFollowRedirects(true);
        getPage(parameter);
        String page = br.toString();
        String fpName = br.getRegex("(?:og:)?(?:title|description)\" content=[\"'](?:\\s*Watch\\s*)?([^\"\']+)(?:\\s+online streaming free|\\s+Online\\s+\\|\\s+Couchtuner)").getMatch(0);
        String[][] links = br.getRegex("<strong>Watch it here\\s*:</strong>\\s*</span>\\s*<a href=\"([^\"\']+)\"").getMatches();
        if (links == null || links.length == 0) {
            links = br.getRegex("Watch [iI]t [hH]ere\\s*:\\s*</span>&nbsp;\\s+<a href=\"([^\"]+)\"").getMatches();
        }
        if (links == null || links.length == 0) {
            links = br.getRegex("<iframe[^>]+src=[\"\']([^\"\']+)[\"\']").getMatches();
        }
        if (links == null || links.length == 0) {
            links = br.getRegex("<a[\r\\n ]+href=\"([^\"]+)\" rel=\"bookmark\">").getMatches();
        }
        if (links == null || links.length == 0) {
            links = br.getRegex("<iframe[^>]+src=\"([^\"]+)\"[^>]*>").getMatches();
        }
        for (String[] link : links) {
            link[0] = Encoding.htmlDecode(link[0]).replaceAll("^//", "https://");
            decryptedLinks.add(createDownloadlink(link[0]));
        }
        if (fpName != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }
}