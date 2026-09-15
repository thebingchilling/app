// Titanium fork: see developer_private_install_extension_file_decl.inc for
// context. This mirrors DeveloperPrivateLoadUnpackedFunction's
// ShowSelectFileDialog() (same ui::SelectFileDialog::Listener pattern, just
// SELECT_OPEN_FILE instead of SELECT_EXISTING_FOLDER) and
// DeveloperPrivateInstallDroppedFileFunction::Run()'s crx/zip install
// dispatch verbatim.
DeveloperPrivateInstallExtensionFileFunction::
    DeveloperPrivateInstallExtensionFileFunction() = default;
DeveloperPrivateInstallExtensionFileFunction::
    ~DeveloperPrivateInstallExtensionFileFunction() = default;

ExtensionFunction::ResponseAction
DeveloperPrivateInstallExtensionFileFunction::Run() {
  ShowSelectFileDialog();
  AddRef();  // Balanced in Finish.
  return RespondLater();
}

void DeveloperPrivateInstallExtensionFileFunction::ShowSelectFileDialog() {
  DCHECK_CURRENTLY_ON(content::BrowserThread::UI);

  content::WebContents* web_contents = GetSenderWebContents();
  CHECK(web_contents);
  select_file_dialog_ = ui::SelectFileDialog::Create(
      this, std::make_unique<ChromeSelectFilePolicy>(web_contents));

  ui::SelectFileDialog::FileTypeInfo file_type_info;
  file_type_info.extensions.push_back({FILE_PATH_LITERAL("crx")});
  file_type_info.extensions.push_back({FILE_PATH_LITERAL("zip")});
  file_type_info.include_all_files = true;

  gfx::NativeWindow owning_window =
      platform_util::GetTopLevel(web_contents->GetNativeView());

  select_file_dialog_->SelectFile(
      ui::SelectFileDialog::SELECT_OPEN_FILE, std::u16string(),
      base::FilePath(), &file_type_info, 0, base::FilePath::StringType(),
      owning_window);
}

void DeveloperPrivateInstallExtensionFileFunction::FileSelected(
    const ui::SelectedFileInfo& file,
    int index) {
  select_file_dialog_.reset();
  base::FilePath file_path = file.path();
#if BUILDFLAG(IS_ANDROID)
  // SelectFileDialog returns a content URI on Android; resolve it to a
  // virtual document path the same way LoadUnpacked does.
  std::optional<base::FilePath> vp =
      base::ResolveToVirtualDocumentPath(file_path);
  if (!vp) {
    Finish(Error("Failed to resolve selected file (removed?)"));
    return;
  }
  file_path = *vp;
#endif  // BUILDFLAG(IS_ANDROID)
  InstallFile(file_path);
}

void DeveloperPrivateInstallExtensionFileFunction::FileSelectionCanceled() {
  select_file_dialog_.reset();
  Finish(Error("File selection was canceled."));
}

void DeveloperPrivateInstallExtensionFileFunction::InstallFile(
    const base::FilePath& file_path) {
  content::WebContents* web_contents = GetSenderWebContents();
  if (!web_contents) {
    Finish(Error(kCouldNotFindWebContentsError));
    return;
  }

  if (file_path.MatchesExtension(FILE_PATH_LITERAL(".zip"))) {
    ExtensionRegistrar* registrar =
        ExtensionRegistrar::Get(browser_context());
    ZipFileInstaller::Create(
        GetExtensionFileTaskRunner(),
        MakeRegisterInExtensionServiceCallback(browser_context()))
        ->InstallZipFileToUnpackedExtensionsDir(
            file_path, registrar->unpacked_install_directory());
  } else if (file_path.MatchesExtension(FILE_PATH_LITERAL(".crx"))) {
    auto prompt = std::make_unique<ExtensionInstallPrompt>(
        web_contents, std::make_unique<extensions::InstallPromptData>(
                          extensions::InstallPromptData::UNSET_PROMPT_TYPE));
    scoped_refptr<CrxInstaller> crx_installer =
        CrxInstaller::Create(browser_context(), std::move(prompt));
    crx_installer->set_error_on_unsupported_requirements(true);
    crx_installer->set_off_store_install_allow_reason(
        CrxInstaller::OffStoreInstallAllowedFromSettingsPage);
    crx_installer->set_install_immediately(true);
    crx_installer->InstallCrx(file_path);
  } else {
    Finish(Error("Selected file must be a .crx or .zip extension package."));
    return;
  }

  Finish(NoArguments());
}

void DeveloperPrivateInstallExtensionFileFunction::Finish(
    ResponseValue response_value) {
  Respond(std::move(response_value));
  Release();  // Balanced in Run().
}
