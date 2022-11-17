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

import score.Address;
import score.Context;

import java.math.BigInteger;

public class TokenProxy {
    public static final String IRC2 = "irc-2";
    public static final String IRC31 = "irc-31";
    public static final String CROWN = "crown";
    public static final String X_CROWN = "x_crown";

    private final Address address;
    private final String type;
//    private final BigInteger id;

    public TokenProxy(Address address, String type) {
        Context.println("LOG 2 ---" + address + type);
        Context.require(address != null, "TokenAddressNotSet");
        this.address = address;
        this.type = type;
    }

    public BigInteger getCurrentSnapshotId(Address caller) {
        if (IRC2.equals(type)) {
            return Context.call(BigInteger.class, address, "getCurrentSnapshotId", caller);
        } else {
            return BigInteger.ZERO;
        }
    }

    // TODO: balanceOfCrownAt
    public BigInteger balanceOfAt(Address holder, int votingSnapshotId) {
        if (IRC2.equals(type)) {
            return Context.call(BigInteger.class, address, "balanceOfAt", holder, votingSnapshotId);
        } else {
//            return Context.call(BigInteger.class, address, "balanceOf", holder, id);
            return BigInteger.ZERO;
        }
    }

    public BigInteger balanceOfCrownAt(Address holder, int votingSnapshotId) {
        if (IRC2.equals(type)) {
            return Context.call(BigInteger.class, address, "balanceOfCrownAt", holder, votingSnapshotId);
        } else {
//            return Context.call(BigInteger.class, address, "balanceOf", holder, id);
            return BigInteger.ZERO;
        }
    }

    public BigInteger balanceOf(Address holder) {
        if (IRC2.equals(type)) {
            return Context.call(BigInteger.class, address, "balanceOf", holder);
        } else {
//            return Context.call(BigInteger.class, address, "balanceOf", holder, id);
            return BigInteger.ZERO;
        }
    }

    public BigInteger generateSnapshotAndFetchId() {
        Context.println("LOG 3 --- generateSnapshot");
        Context.call(BigInteger.class, address, "snapshot");
        return Context.call(BigInteger.class, address, "getCurrentSnapshotId");
    }

    public BigInteger getCrownPerXCrown(Address bankAddress) {
        return Context.call(BigInteger.class, bankAddress, "getCrownPerXCrown");
    }
}