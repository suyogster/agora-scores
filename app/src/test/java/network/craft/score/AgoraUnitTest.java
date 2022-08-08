/*
 * Copyright 2022 Craft Network
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

package network.craft.score;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgoraUnitTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private Score agoraScore;

    @BeforeEach
    void setup() throws Exception {
        agoraScore = sm.deploy(owner, AgoraImpl.class);
    }

    @Test
    void name() {
        assertEquals("AgoraScore", agoraScore.call("name"));
    }
}
