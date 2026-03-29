import 'package:docman/src/exceptions/app_dir_path.dart';
import 'package:docman/src/exceptions/common_exceptions.dart';
import 'package:docman/src/exceptions/document_file_exception.dart';
import 'package:docman/src/exceptions/permissions_exceptions.dart';
import 'package:docman/src/exceptions/picker_exceptions.dart';
import 'package:flutter/services.dart' show PlatformException;

/// Extensions for [PlatformException] class.
extension PlatformExceptionExt on PlatformException {
  /// Throws a custom exception based on the error [code].
  ///
  /// If the [code] is 'already_running', it throws an [AlreadyRunningException]
  /// with the provided [message]. For any other [code] not in list, it rethrows the original
  /// [PlatformException].
  Never throwByCode() {
    if (code == AlreadyRunningException.tag) {
      throw AlreadyRunningException(message);
    } else if (code == NoActivityException.tag) {
      throw NoActivityException(message);
    } else if (code == AppDirPathException.tag) {
      throw AppDirPathException(message);
    } else if (code == AppDirActionException.tag) {
      throw AppDirActionException(message);
    } else if (code == PickerMimeTypeException.tag) {
      throw PickerMimeTypeException(message);
    } else if (code == PickerMaxLimitException.tag) {
      throw PickerMaxLimitException(message);
    } else if (code == PickerCountException.tag) {
      throw PickerCountException(message, details as String);
    } else if (code == DocumentFileException.tag) {
      throw DocumentFileException(message);
    } else if (code == PermissionsException.tag) {
      throw PermissionsException(message);
    } else {
      throw this;
    }
  }
}
