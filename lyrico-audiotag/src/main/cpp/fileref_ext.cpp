/***************************************************************************
    copyright            : (C) 2002 - 2008 by Scott Wheeler
    email                : wheeler@kde.org

    copyright            : (C) 2010 by Alex Novichkov
    email                : novichko@atnet.ru
                           (added APE file support)
 ***************************************************************************/

/***************************************************************************
 *   This library is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Lesser General Public License version   *
 *   2.1 as published by the Free Software Foundation.                     *
 *                                                                         *
 *   This library is distributed in the hope that it will be useful, but   *
 *   WITHOUT ANY WARRANTY; without even the implied warranty of            *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU     *
 *   Lesser General Public License for more details.                       *
 *                                                                         *
 *   You should have received a copy of the GNU Lesser General Public      *
 *   License along with this library; if not, write to the Free Software   *
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA         *
 *   02110-1301  USA                                                       *
 *                                                                         *
 *   Alternatively, this file is available under the Mozilla Public        *
 *   License Version 1.1.  You may obtain a copy of the License at         *
 *   http://www.mozilla.org/MPL/                                           *
 ***************************************************************************/

#include "fileref_ext.h"

#include <cstring>
#include <utility>

#include "tfilestream.h"
#include "tpropertymap.h"
#include "tstringlist.h"
#include "tvariant.h"
#include "aifffile.h"
#include "apefile.h"
#include "asffile.h"
#include "flacfile.h"
#include "mp4file.h"
#include "mpegfile.h"
#include "oggflacfile.h"
#include "opusfile.h"
#include "vorbisfile.h"
#include "wavfile.h"
#include "wavpackfile.h"
#include "dsffile.h"
#include "dsdifffile.h"

using namespace TagLib;

namespace TagLibExt {
    // Detect the file type based on the file extension.

    File *detectByExtension(FileName *fileName, IOStream *stream, bool readAudioProperties,
                            AudioProperties::ReadStyle audioPropertiesStyle) {
        FileName path = stream->name();
        if (fileName != nullptr) {
            path = *fileName;
        }
        const String s(path);

        String ext;
        if (const int pos = s.rfind("."); pos != -1)
            ext = s.substr(pos + 1).upper();

        // If this list is updated, the method defaultFileExtensions() should also be
        // updated.  However at some point that list should be created at the same time
        // that a default file type resolver is created.

        if (ext.isEmpty())
            return nullptr;

        // .oga can be any audio in the Ogg container. So leave it to content-based detection.

        File *file = nullptr;

        if (ext == "MP3" || ext == "MP2" || ext == "AAC")
            file = new MPEG::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "OGG")
            file = new Ogg::Vorbis::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "OGA") {
            /* .oga can be any audio in the Ogg container. First try FLAC, then Vorbis. */
            file = new Ogg::FLAC::File(stream, readAudioProperties, audioPropertiesStyle);
            if (!file->isValid()) {
                delete file;
                file = new Ogg::Vorbis::File(stream, readAudioProperties, audioPropertiesStyle);
            }
        } else if (ext == "FLAC")
            file = new FLAC::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "WV")
            file = new WavPack::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "OPUS")
            file = new Ogg::Opus::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "M4A" || ext == "M4R" || ext == "M4B" || ext == "M4P" || ext == "MP4" || ext == "3G2" ||
                 ext == "M4V")
            file = new MP4::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "WMA" || ext == "ASF")
            file = new ASF::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "AIF" || ext == "AIFF" || ext == "AFC" || ext == "AIFC")
            file = new RIFF::AIFF::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "WAV")
            file = new RIFF::WAV::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "APE")
            file = new APE::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "DSF")
            file = new DSF::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ext == "DFF" || ext == "DSDIFF")
            file = new DSDIFF::File(stream, readAudioProperties, audioPropertiesStyle);

        // if file is not valid, leave it to content-based detection.

        if (file) {
            if (file->isValid())
                return file;
            delete file;
        }

        return nullptr;
    }

    // Detect the file type based on the actual content of the stream.

    File *detectByContent(IOStream *stream, bool readAudioProperties,
                          AudioProperties::ReadStyle audioPropertiesStyle) {
        File *file = nullptr;

        if (MPEG::File::isSupported(stream))
            file = new MPEG::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (Ogg::Vorbis::File::isSupported(stream))
            file = new Ogg::Vorbis::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (Ogg::FLAC::File::isSupported(stream))
            file = new Ogg::FLAC::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (FLAC::File::isSupported(stream))
            file = new FLAC::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (WavPack::File::isSupported(stream))
            file = new WavPack::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (Ogg::Opus::File::isSupported(stream))
            file = new Ogg::Opus::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (MP4::File::isSupported(stream))
            file = new MP4::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (ASF::File::isSupported(stream))
            file = new ASF::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (RIFF::AIFF::File::isSupported(stream))
            file = new RIFF::AIFF::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (RIFF::WAV::File::isSupported(stream))
            file = new RIFF::WAV::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (APE::File::isSupported(stream))
            file = new APE::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (DSF::File::isSupported(stream))
            file = new DSF::File(stream, readAudioProperties, audioPropertiesStyle);
        else if (DSDIFF::File::isSupported(stream))
            file = new DSDIFF::File(stream, readAudioProperties, audioPropertiesStyle);

        // isSupported() only does a quick check, so double check the file here.

        if (file) {
            if (file->isValid())
                return file;
            delete file;
        }

        return nullptr;
    }

    class FileRef::FileRefPrivate {
    public:
        FileRefPrivate() = default;

        ~FileRefPrivate() {
            delete file;
        }

        FileRefPrivate(const FileRefPrivate &) = delete;

        FileRefPrivate &operator=(const FileRefPrivate &) = delete;

        [[nodiscard]] bool isNull() const {
            return !file || !file->isValid();
        }

        File *file{nullptr};
    };

////////////////////////////////////////////////////////////////////////////////
// public members
////////////////////////////////////////////////////////////////////////////////

    FileRef::FileRef() :
            d(std::make_shared<FileRefPrivate>()) {
    }

    FileRef::FileRef(FileName fileName, IOStream *stream, bool readAudioProperties,
                     AudioProperties::ReadStyle audioPropertiesStyle) :
            d(std::make_shared<FileRefPrivate>()) {
        parse(fileName, stream, readAudioProperties, audioPropertiesStyle);
    }

    FileRef::FileRef(File *file) :
            d(std::make_shared<FileRefPrivate>()) {
        d->file = file;
    }

    FileRef::FileRef(const FileRef &) = default;

    FileRef::~FileRef() = default;

    TagLib::Tag *FileRef::tag() const {
        if (d->isNull()) {
            return nullptr;
        }
        return d->file->tag();
    }

    PropertyMap FileRef::properties() const {
        if (d->isNull()) {
            return {};
        }
        return d->file->properties();
    }

    void FileRef::removeUnsupportedProperties(const StringList &properties) {
        if (d->isNull()) {
            return;
        }
        return d->file->removeUnsupportedProperties(properties);
    }

    PropertyMap FileRef::setProperties(const PropertyMap &properties) {
        if (d->isNull()) {
            return {};
        }
        return d->file->setProperties(properties);
    }

    StringList FileRef::complexPropertyKeys() const {
        if (d->isNull()) {
            return {};
        }
        return d->file->complexPropertyKeys();
    }

    List<VariantMap> FileRef::complexProperties(const String &key) const {
        if (d->isNull()) {
            return {};
        }
        return d->file->complexProperties(key);
    }

    bool FileRef::setComplexProperties(const String &key, const List<VariantMap> &value) {
        if (d->isNull()) {
            return false;
        }
        return d->file->setComplexProperties(key, value);
    }

    AudioProperties *FileRef::audioProperties() const {
        if (d->isNull()) {
            return nullptr;
        }
        return d->file->audioProperties();
    }

    File *FileRef::file() const {
        return d->file;
    }

    bool FileRef::save() {
        if (d->isNull()) {
            return false;
        }
        return d->file->save();
    }

    bool FileRef::isNull() const {
        return d->isNull();
    }

    FileRef &FileRef::operator=(const FileRef &) = default;

    void FileRef::swap(FileRef &ref) noexcept {
        using std::swap;

        swap(d, ref.d);
    }

    bool FileRef::operator==(const FileRef &ref) const {
        return ref.d->file == d->file;
    }

    bool FileRef::operator!=(const FileRef &ref) const {
        return ref.d->file != d->file;
    }

////////////////////////////////////////////////////////////////////////////////
// private members
////////////////////////////////////////////////////////////////////////////////

    void FileRef::parse(FileName fileName,
                        IOStream *stream,
                        bool readAudioProperties,
                        AudioProperties::ReadStyle audioPropertiesStyle) {
        // Try to resolve file types based on the file extension.

        d->file = detectByExtension(&fileName, stream, readAudioProperties, audioPropertiesStyle);
        if (d->file)
            return;

        // At last, try to resolve file types based on the actual content.

        d->file = detectByContent(stream, readAudioProperties, audioPropertiesStyle);
    }

}  // namespace
