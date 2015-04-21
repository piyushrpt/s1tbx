/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.s1tbx.dat.actions;

import org.esa.snap.BeamUiActivator;
import org.esa.snap.framework.ui.command.CommandEvent;
import org.esa.snap.framework.ui.layer.LayerSourceAssistantPane;
import org.esa.snap.framework.ui.layer.LayerSourceDescriptor;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.layermanager.layersrc.SelectLayerSourceAssistantPage;
import org.esa.snap.visat.VisatApp;
import org.esa.snap.visat.actions.AbstractVisatAction;

/**
 * This action opens a vector dataset
 *
 * @author lveci
 * @version $Revision: 1.7 $ $Date: 2011-04-08 18:23:59 $
 */
public class OpenVectorAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {

        final LayerSourceAssistantPane pane = new LayerSourceAssistantPane(VisatApp.getApp().getApplicationWindow(),
                "Add Layer");
        final LayerSourceDescriptor[] layerSourceDescriptors = BeamUiActivator.getInstance().getLayerSources();
        pane.show(new SelectLayerSourceAssistantPage(layerSourceDescriptors));
    }

    @Override
    public void updateState(final CommandEvent event) {
        event.getCommand().setEnabled(SnapApp.getDefault().getSelectedProductSceneView() != null);
    }
}
