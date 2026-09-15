// Titanium fork: adds developerPrivate.installExtensionFile(), a single-file
// counterpart to loadUnpacked()/installDroppedFile(). Android has no
// drag-and-drop from a file manager onto a web page, and "Load unpacked"
// only accepts a directory (via Storage Access Framework), so there was no
// way to install an already-packed .crx/.zip picked from local storage.
//
// This reuses the exact same ui::SelectFileDialog flow as
// DeveloperPrivateLoadUnpackedFunction (just SELECT_OPEN_FILE instead of
// SELECT_EXISTING_FOLDER), and the exact same crx/zip install dispatch as
// DeveloperPrivateInstallDroppedFileFunction -- see
// developer_private_install_extension_file_impl.inc.
//
// Registered under DEVELOPERPRIVATE_INSTALLDROPPEDFILE's histogram value
// (its nearest sibling) rather than adding a new entry to the generated
// extension_function_histogram_value.h enum.
class DeveloperPrivateInstallExtensionFileFunction
    : public DeveloperPrivateAPIFunction,
      public ui::SelectFileDialog::Listener {
 public:
  DECLARE_EXTENSION_FUNCTION("developerPrivate.installExtensionFile",
                             DEVELOPERPRIVATE_INSTALLDROPPEDFILE)
  DeveloperPrivateInstallExtensionFileFunction();

  DeveloperPrivateInstallExtensionFileFunction(
      const DeveloperPrivateInstallExtensionFileFunction&) = delete;
  DeveloperPrivateInstallExtensionFileFunction& operator=(
      const DeveloperPrivateInstallExtensionFileFunction&) = delete;

  // ui::SelectFileDialog::Listener:
  void FileSelected(const ui::SelectedFileInfo& file, int index) override;
  void FileSelectionCanceled() override;

 protected:
  ~DeveloperPrivateInstallExtensionFileFunction() override;

  // DeveloperPrivateAPIFunction:
  ResponseAction Run() override;

 private:
  void ShowSelectFileDialog();
  void InstallFile(const base::FilePath& file_path);
  void Finish(ResponseValue response_value);

  scoped_refptr<ui::SelectFileDialog> select_file_dialog_;
};
