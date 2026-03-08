#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "TagLib::tag" for configuration "Release"
set_property(TARGET TagLib::tag APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(TagLib::tag PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libtag.a"
  )

list(APPEND _cmake_import_check_targets TagLib::tag )
list(APPEND _cmake_import_check_files_for_TagLib::tag "${_IMPORT_PREFIX}/lib/libtag.a" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
