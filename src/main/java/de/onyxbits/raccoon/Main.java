/*
 * Copyright 2015 Patrick Ahlbrecht
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.onyxbits.raccoon;

import java.awt.EventQueue;
import java.sql.SQLException;


import de.onyxbits.raccoon.appmgr.DetailsViewBuilder;
import de.onyxbits.raccoon.appmgr.MyAppsViewBuilder;
import de.onyxbits.raccoon.db.DatabaseManager;
import de.onyxbits.raccoon.db.VariableDao;
import de.onyxbits.raccoon.gplay.ImportBuilder;
import de.onyxbits.raccoon.gplay.ManualDownloadBuilder;
import de.onyxbits.raccoon.gplay.PlayManager;
import de.onyxbits.raccoon.gui.GrantBuilder;
import de.onyxbits.raccoon.gui.MainLifecycle;
import de.onyxbits.raccoon.gui.UnavailableBuilder;
import de.onyxbits.raccoon.net.ServerManager;
import de.onyxbits.raccoon.ptools.BridgeManager;
import de.onyxbits.raccoon.qr.QrToolBuilder;
import de.onyxbits.raccoon.qr.ShareToolBuilder;
import de.onyxbits.raccoon.rss.FeedTask;
import de.onyxbits.raccoon.setup.WizardLifecycle;
import de.onyxbits.raccoon.transfer.TransferViewBuilder;
import de.onyxbits.raccoon.vfs.Layout;
import de.onyxbits.weave.LifecycleManager;

/**
 * Application Launcher
 * 
 * @author patrick
 * 
 */
public final class Main {

	public static long now = System.currentTimeMillis();

	/*
	 * Start the application.
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		if (args == null || args.length == 0) {
			startGui();
		}
		else {

		}
	}

	/**
	 * Fire up the GUI. This method blocks till the user closes the primary
	 * window.
	 * 
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 */
	private static void startGui() throws Exception, Exception {
		// TBD: Should we really force a LAF? This costs us a good 100 ms startup
		// time and some people prefer platform LAF.
		/*
		 * if (System.getProperty("swing.defaultlaf") == null) { // Unless the user
		 * specifically wants something else, try forcing the // Nimbus L&F for
		 * (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) { //
		 * System.err.println(info.getName()); if ("Nimbus".equals(info.getName()))
		 * { try { UIManager.setLookAndFeel(info.getClassName()); //
		 * UIManager.getLookAndFeelDefaults().put("Panel.background", //
		 * java.awt.Color.decode("#EDECEB")); } catch (Exception e) { } } } }
		 */

		// Startup speed matters big time! So in order to show the UI ASAP, use
		// every bit of idle time to force the classloader into resolving bytecode
		// and initializing constants ahead of time.
		Object preload = null;

		// Pre GUI initialization
		DatabaseManager database = new DatabaseManager(Layout.DEFAULT.databaseDir);
		ServerManager serverManager = new ServerManager(Layout.DEFAULT);
		BridgeManager bridgeManager = new BridgeManager(Layout.DEFAULT);
		database.startup();
		System.err.println("Time to DB: " + (System.currentTimeMillis() - now));

		String alias = database.get(VariableDao.class).getVar(
				VariableDao.PLAYPASSPORT_ALIAS, null);
		PlayManager playManager = new PlayManager(database);
		playManager.selectProfile(alias);

		LifecycleManager lifecycle = null;

		// Bring up the Setup Wizard (if needed)
		if (alias == null) {
			lifecycle = new LifecycleManager(new WizardLifecycle(database,
					playManager, null));
			EventQueue.invokeLater(lifecycle);
			lifecycle.waitForState(LifecycleManager.FINISHED);
			database.get(VariableDao.class).setVar(VariableDao.CREATED,
					"" + System.currentTimeMillis());
			if (playManager.getActiveProfile() == null) {
				// User closed the setup wizard -> quit
				database.shutdown();
				return;
			}
		}

		// Bring up the Main UI.
		lifecycle = new LifecycleManager(new MainLifecycle(database, serverManager,
				bridgeManager, playManager));
		EventQueue.invokeLater(lifecycle);
		preload = de.onyxbits.raccoon.gui.Messages.BUNDLE_NAME;
		preload = de.onyxbits.raccoon.gplay.Messages.BUNDLE_NAME;

		// Required post GUI initialization.
		lifecycle.waitForState(LifecycleManager.RUNNING);
		System.err.println("Time to UI: " + (System.currentTimeMillis() - now));

		bridgeManager.startup();
		serverManager.startup(lifecycle);

		// Optional post GUI initialization
		preload = de.onyxbits.raccoon.appmgr.Messages.BUNDLE_NAME;
		preload = de.onyxbits.raccoon.transfer.Messages.BUNDLE_NAME;
		preload = de.onyxbits.raccoon.ptools.Messages.BUNDLE_NAME;
		preload = de.onyxbits.raccoon.qr.Messages.BUNDLE_NAME;
		preload = QrToolBuilder.ID;
		preload = UnavailableBuilder.ID;
		preload = ImportBuilder.ID;
		preload = ShareToolBuilder.ID;
		preload = MyAppsViewBuilder.ID;
		preload = GrantBuilder.ID;
		preload = DetailsViewBuilder.ID;
		preload = TransferViewBuilder.ID;
		preload = ManualDownloadBuilder.ID;

		new FeedTask(lifecycle, database).run();
		new VersionTask(lifecycle).run();

		// Shutdown
		lifecycle.waitForState(LifecycleManager.FINISHED);
		bridgeManager.shutdown();
		database.shutdown();
		serverManager.shutdown();
		preload.toString(); // Saves us a @SuppressWarnings("unused")
	}
}