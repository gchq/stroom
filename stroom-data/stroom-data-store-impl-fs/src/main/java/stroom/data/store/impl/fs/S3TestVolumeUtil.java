package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.AwsBasicCredentials;
import stroom.data.store.impl.fs.shared.AwsCredentialsProviderType;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.data.store.impl.fs.shared.S3ClientConfig;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

public class S3TestVolumeUtil {

    public static void alterVolumes(final FsVolumeService fsVolumeService) {
        final S3ClientConfig s3ClientConfig = S3ClientConfig
                .builder()
                .credentialsProviderType(AwsCredentialsProviderType.STATIC)
                .credentials(AwsBasicCredentials
                        .builder()
                        .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                        .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                        .build())
                .region("us-west-2")
                .endpointOverride("http://localhost:9444")
                .build();

        // Make all existing volumes S3 volumes.
        final ResultPage<FsVolume> volumes = fsVolumeService.find(FindFsVolumeCriteria.matchAll());
        volumes.forEach(volume -> {
            volume.setVolumeType(FsVolumeType.S3);
            volume.setS3ClientConfig(s3ClientConfig);
            volume.setS3ClientConfigData(JsonUtil.writeValueAsString(s3ClientConfig));
            volume.setPath("test");
            fsVolumeService.update(volume);
        });
    }
}
