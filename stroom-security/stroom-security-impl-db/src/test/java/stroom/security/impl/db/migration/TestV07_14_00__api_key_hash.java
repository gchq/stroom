/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.impl.db.migration;

import stroom.util.json.JsonUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

class TestV07_14_00__api_key_hash {

    // A load of API keys that were hashed in 7.13 using hex encoding. Each one also has a base58 hash
    // that was encoded in 7.14 so we can make sure our decoding+encoding works.
    @SuppressWarnings("checkstyle:LineLength")
    private static final String LEGACY_DATA = """
            [ {
              "inputText" : "sak_66e3d1414a_xFyAWU1cLMyRsYcg8QkqfHMMzboZ2q28MSoPs7B7wzMjSuMULHZkBsC8CnnUMdfQAon5q4RPXjVhawuMc96U42ff9NcuddrMQ9PeJztTECtfuzEst9undfX7HJtXmrDy",
              "hexHash" : "91793091e021c11ac60f63e2f085a54a30559a381e1c9aaaf59c48ce8a64ced9",
              "base58Hash" : "AnsJE41S3jZ2s7dH7AopdugRXCvN6RFTkXTB4uZqnpCx",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_a163f9508b_jqffgkoGZgsja9g4ioZrFB9NNqCbQUWaUQjHuZ31MjSMPtAyGRwJTpL9ZV6gPZPhVdGXqeGVy6E7CLBM5226s2fEXdfH29wictgVikqkjCWkw8EbWcDqCSvdVX9XnwPk",
              "hexHash" : "ba1a3ffa058a0ba208a17e8e4bccc8afc6e9cd3be583ab550d2e8dd88de5c910",
              "base58Hash" : "DXU3kEpVaVkDzrBXwSVgPvciSeaD4Ld8vzKp51vC5rjy",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_c30330fa0a_4nYERAfKJWw6mgrjk85K4Gft45fRMDA7BBwN9fo9kCGgVvAmMU7JH9JX2KHHGS4NNfdttuLKBdMFZknznVuEtmuBEEi8KAc8hzr4mJr8SEj5hUShdjFmr6AvKzNrcFWp",
              "hexHash" : "635c8af20fefd7d731ab615519ec4a2e7eafedbf3cf6b8b671bbb347402a4f8f",
              "base58Hash" : "7gsD2ZccceoA7Z1zaVjiKTcdyMn9DFqh62Y7zf4KXXxr",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_1735e2b674_KNP6hFZd43QakuYhfqoQ7rWvpF7iGGWedMSaGUkXcFQUP2ELbjmM7vePmQUVpetrhfo1qqNCxRyxLKtyVPBLQVMBMfVk7z3cGr69Ebi5FbZh29ZzaRUCaD7ej1chTxvS",
              "hexHash" : "cad85c4d7e99d727d124ab233cb33449c757f2e6b740cd88012d62856f1c3bf9",
              "base58Hash" : "EephuhiEZduTvnaAopvsJ1WXWaD9cgYSNsxmCjQ8sxMN",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_ef8abb4659_vjwHBXKYGywPmT78wqLbr7tibvonxCmUnVVHW9rD6YdQNTX9xwFrozmipETA9EiwFHU2yFstcgc9qd8ACJPgunK1V1Exh6KWg5LbAanuvUD6Zukmjc3VnSBXhfdQD5ee",
              "hexHash" : "4f0fd33f8ebc4e171331375dc6c2119cdb9c1d47c29d727f36f6e9114e9f31f8",
              "base58Hash" : "6KdCqFscWu3jUKZ3Q734tuqyNS9DPeLiQXncT6xBK6vs",
              "hashAlgorithm" : "SHA3_256"
            }, {
              "inputText" : "sak_2b38bebe4f_6PFFFP7ZXcRBFvu8h1EfevTzBvRybRe28niDFASpxAg4i8JP6e7g8GsSgqEZZUxqrRHYvmYAuNSd48G3BvMPbVLHH6xQHqmJZAZENk4Ydv7zQ2ChLXRqA5XFrNMq9Cpw",
              "hexHash" : "cc5b8fb42303987c9641c5c833c2b17781c5b8c3c047b9a9ac6181191fb56db2",
              "base58Hash" : "Ekj9Ziyy3nfxvuwjRU4f7LRS6s28juU1PjmBQche42aD",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_cf76142e34_z7PMw3Nd8ofpFJSPnQYu2kbftFqYHqrQqPP6M41RBZoj55JWfFiVQndMjLdxEexnRNfUey1m1ktzWPAnM3WSfC4wEjLTprQTU5XGTErEGXv98vj21ZuoyXvnpVxykseG",
              "hexHash" : "ccbd8c030dea745ce2ba876521a8519e65f8ea2a89015218bbb06e8454037a5c",
              "base58Hash" : "EnDonQfKrnSWkT8j4TY9axHRs7yKnXPu7zy5fYfyKVjR",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_d3b5ae203f_URpBwC9CaZTCdQPX4ieaANzsxT7C1nZJTMciJkVwEdezqYmXtX4oTgKWSzLcakcmzD1Lmeo4JvsEH28LgMsDXAmQ7Ve9v2GmKUTJBeatMteV9czL1yUnRzopeJesQrXv",
              "hexHash" : "171800e34bc95a1ba558b9645d2728275667e755f9fc09a5398ea663d9a6f620",
              "base58Hash" : "2Z9c93AuHXY8gFNgZmBZ1iEHqQVUty7LP8Pn6oc1hQHd",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_286d5745d5_6hBTqiub9XVTof7s7yUmAVW47qadYNSxBwjdgVprFFGc1eTAavs5EQJsfbpmPF3FjPqsrodaZ9AicCJtwrbD3AvjLKHes9uWDfipVS6jxnJuXAWBzYNfsF9Fx4wCqJJz",
              "hexHash" : "84a29b7f177d7b6790b07a8767e2c45337c90682d57bb9079132e6d8351fd394",
              "base58Hash" : "9vkdMxhoQvAYxRPVM1wBkd5Y1oT7SsvT6MjLiery1BwH",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_be9db44134_VeeBYWjLPedqpQMyi5XBNCbvThpp56MRtJUBMDNbw6FEqRFQxAvHy44NGfDf849P8SUntgs7zi92xKophjFVZawiHRiDxyXG88y1V49LAaaD2dGFwRTwQR87UTviGRUx",
              "hexHash" : "4923fd4cc7e0068c10e1528824532ad93d923660cbba9ec6bf3cb6b03ae80f2b",
              "base58Hash" : "5vWbHmUiKKmmN31j2qKGE1L4hjG5PcYY9v4yg8HHVY6E",
              "hashAlgorithm" : "SHA2_256"
            }, {
              "inputText" : "sak_d8faa91a5c_2DfK3WCtXV69uefNWVnsKt25JHoLRFB7eX3uvBd1Fu9tcagUo6oXk3n8ZkcA7XJqpsr9dtSsGiEBK65dAHhhNokhfsWX6ds3rwjBh39wD1vvz3b1DSdYTHtbfXX29hUW",
              "hexHash" : "2730bf1c922619a2ed66671dbfa5c62465506b01bd0aee53714b5583d96311fa5dc261dc951e5a9f86c8bf670d3dd37dee34d72078c0645ea504537b5b4c8ea2",
              "base58Hash" : "nSqSGm6hkhXvTJa9BB7kwgiUjCxrf3FTK8ATnCf1muAQ5gvnKzyP4fEXcdei3DF6Uosp1NQtZB3nQWoHqwSqkHf",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_601a56cf18_ptaYuWBLBF8eqfgEJMfcvfV8DmY4R2c7TG7g3kMjNFzmDDdn6Ue1n4sRNN4RsTZKVaXyktRMfcrmKED9xvEiH27adXd3nfvPrUJGuYMwAbiMRaLax6zGuncZigDEGYVb",
              "hexHash" : "7c133b1409519172f96750d954192681fcccebe257ba1a516b5a28e43b0488826358261927b8b997b8085c25f7db710b6112c83ec78811a84baebf9c7ce49cd5",
              "base58Hash" : "3UswMLNZgrWdSGw2C1h2mqCP3qJ461Apc63pfHYFz658FTp3T9KvFP2tMqD5ZNCb89jmv6iyZPG7C1cAmvgd1s9W",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_df2559cd84_idckXCzQ2kJq8hCxjKzgARuDG79wCujjVtaidhnXakMJt2PwQNLCxgB1nKKJJCLGDE5A1hFSBJ1bCgTPRFhysTiyW7jd8vwLWjUP2rds67qkUNZRNHNfNP7vxmhUnNVM",
              "hexHash" : "17d5b49577bee4ceb88c95341c32e014eb9a3707593bb6934d6e18eb2c5026aebe6965f1deaac63df9464be779d49e38c2de483f1b390b31606d185c75e64635",
              "base58Hash" : "Ue4T33tZwavXyV2GVLqojJRzxpzuUT5GkzzPdNcdDPood9iAzK9ntibDn6wzANMq9QfkMBM1W8RMo2a3iyR5LYc",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_230782c3bf_U9XZMr1WhSWsrJWE1S26xQMYGZkUGqpNzbS1fE5ctV5THh7SDnfFudDBru2i2SCzzB2J2H1h6jq9hAhr5ZEUeWdAz1wcFRDzKuAzUHXYfLW2XDbeV2sc8aWXFjWDbRhe",
              "hexHash" : "c7d16ed98c868d9b4ddab9ce0154f7e31ee315a0a82189be8b572d8603467a754c0997ba49656c94bf84af2eebf8c7e9cd2963139e96f3d8bb2b489f65b1ea16",
              "base58Hash" : "4ziCBKCFyWiTdjhDb58Y2UxCUS1oUTQJ5gm2QeGghrfKEFwcnZmkaoa6N7QFwEAWGrpcCS4q8yyic2ZdkjhrCuJd",
              "hashAlgorithm" : "SHA2_512"
            }, {
              "inputText" : "sak_4c5a62e0e7_xMf28DWEZztcpps9t4iMFMXqWp3Z3vjaSyf8T3LoGzaeSjMcVtDnd8ZM2w2tgb4AFgvSNEy7SCYAMM86sDH752Zdmd3MmCfy7vKxP1Y83pZXDSfM3PfesYbczcwdEr2g",
              "hexHash" : "99bc595af5d596fffa030120aedeb7c7d10e88f44f156a3b6f6d20a88e212ec1bcacfd32bb1fc64b7d8f0db11e79fbeb192acc0ab35987006c4d37e6af0e2911",
              "base58Hash" : "45GpsEQwxr6kjJ3tvMJjjPyCRR4iuqKhqENeNERKzvxJ8YCKnz24TzNwzzt5AgoePff91Ued2pnqHPjRqwqcBrqE",
              "hashAlgorithm" : "SHA2_512"
            } ]""";

    @TestFactory
    Stream<DynamicTest> testDecodeEncode() {
        final List<DualHash> hashOutputs = JsonUtil.getMapper()
                .readerFor(new TypeReference<List<DualHash>>() {
                })
                .readValue(LEGACY_DATA);

        final AtomicInteger counter = new AtomicInteger(0);
        return hashOutputs.stream()
                .map(dualHash ->
                        DynamicTest.dynamicTest(
                                counter.incrementAndGet() + " - " + dualHash.hashAlgorithm,
                                () -> {
                                    final String base58Hash = V07_14_00__api_key_hash.hexToBase58(dualHash.hexHash);

                                    Assertions.assertThat(base58Hash)
                                            .isEqualTo(dualHash.base58Hash);

                                    final String hexHash = V07_14_00__api_key_hash.base58ToHex(base58Hash);

                                    Assertions.assertThat(hexHash)
                                            .isEqualTo(dualHash.hexHash);
                                }
                        )
                );
    }


    // --------------------------------------------------------------------------------


    private record DualHash(
            String inputText,
            String hexHash,
            String base58Hash,
            String hashAlgorithm) {

    }
}
