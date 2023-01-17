package com.jetbrains.bigdatatools.common.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.bigdatatools.common.editor.state.EditorPanelState
import com.jetbrains.bigdatatools.common.editor.state.LoadingState
import com.jetbrains.bigdatatools.common.editor.state.ShowErrorState
import com.jetbrains.bigdatatools.common.editor.state.ShowingDelegateState
import com.jetbrains.bigdatatools.common.rfs.localcache.RfsDownloadedStorageManager
import com.jetbrains.bigdatatools.common.settings.fields.ChangeListener
import com.jetbrains.bigdatatools.common.table.editor.LoadingLightVirtualFile
import org.jetbrains.annotations.Nls
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class BdiDecoratableEditor(val initialVirtualFile: VirtualFile,
                           internal val project: Project,
                           @TabTitle private val tabName: String,
                           private val delegateCreator: (VirtualFile, Project) -> FileEditor) : UserDataHolderBase(), FileEditor {

  private var listeners = listOf<ChangeListener>()

  private var currentState: EditorPanelState =
    if (initialVirtualFile is LoadingLightVirtualFile && initialVirtualFile.originalFile != null)
      stateByFile(initialVirtualFile.originalFile)
    else
      stateByFile(initialVirtualFile)

  var delegate: FileEditor? = null
    private set

  internal val mainPanel = BorderLayoutPanel()

  init {
    currentState.enterState()
  }

  /** onChange event fired when */
  fun addChangeListener(listener: ChangeListener) {
    listeners = listeners + listener
  }

  fun removeChangeListener(listener: ChangeListener) {
    listeners = listeners - listener
  }

  override fun dispose() = Unit

  fun startShowing(virtualFile: VirtualFile) {
    if (!isInLoadingState() && currentState !is ShowErrorState)
      return

    changeState(ShowingDelegateState(this, virtualFile))
    (initialVirtualFile as? LoadingLightVirtualFile)?.originalFile = virtualFile
  }

  fun stopWithError(@Nls message: String) {
    if (!isInLoadingState())
      return
    changeState(ShowErrorState(this, message, currentState.tabTitle()))
    listeners.forEach { it.onChange() }
  }

  fun isInLoadingState(): Boolean = currentState is LoadingState

  override fun setState(state: FileEditorState) {
    delegate?.setState(state)
  }

  override fun selectNotify() = service<RfsDownloadedStorageManager>().setFirst(initialVirtualFile)

  override fun getFile(): VirtualFile = delegate?.file ?: initialVirtualFile

  internal fun initDelegate(vFile: VirtualFile) {
    delegate = delegateCreator(vFile, project)
    Disposer.register(this, delegate!!)
    try {
      listeners.forEach { it.onChange() }
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun changeState(newState: EditorPanelState) {
    currentState.exitState()
    currentState = newState
    currentState.enterState()
  }

  private fun stateByFile(vFile: VirtualFile): EditorPanelState =
    if (vFile is LoadingLightVirtualFile) LoadingState(this, vFile) else ShowingDelegateState(this, vFile)

  fun getProject(): Project = project
  override fun getComponent(): BorderLayoutPanel = mainPanel
  override fun getPreferredFocusedComponent(): JComponent? = delegate?.preferredFocusedComponent

  override fun getName(): String = tabName
  override fun isValid(): Boolean = currentState.isValid()
  override fun isModified(): Boolean = false
  override fun getCurrentLocation(): FileEditorLocation? = delegate?.currentLocation
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
}
