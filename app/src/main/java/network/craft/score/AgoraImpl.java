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
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

public class AgoraImpl implements AgoraGov {
    private static final BigInteger DAY_IN_SECONDS = BigInteger.valueOf(86400);
    private static final BigInteger DAY_IN_MICROSECONDS = DAY_IN_SECONDS.multiply(BigInteger.valueOf(1_000_000));
    private static final String IRC2 = "irc-2";
    private static final String IRC31 = "irc-31";

    private final VarDB<Address> tokenAddress = Context.newVarDB("token_address", Address.class);
    private final VarDB<String> tokenType = Context.newVarDB("token_type", String.class);
    private final VarDB<BigInteger> tokenId = Context.newVarDB("token_id", BigInteger.class);
    private final VarDB<BigInteger> minimumThreshold = Context.newVarDB("minimum_threshold", BigInteger.class);

    private final VarDB<BigInteger> proposalId = Context.newVarDB("proposal_id", BigInteger.class);
    private final DictDB<BigInteger, Proposal> proposals = Context.newDictDB("proposals", Proposal.class);

    private void checkCallerOrThrow(Address caller, String errMsg) {
        Context.require(Context.getCaller().equals(caller), errMsg);
    }

    private void onlyOwner() {
        checkCallerOrThrow(Context.getOwner(), "OnlyOwner");
    }

    @External(readonly=true)
    public Map<String, Object> governanceTokenInfo() {
        var type = tokenType();
        if (type == null) {
            return Map.of();
        }
        if (IRC2.equals(type)) {
            return Map.of(
                    "_address", tokenAddress(),
                    "_type", type);
        } else {
            return Map.of(
                    "_address", tokenAddress(),
                    "_type", type,
                    "_id", tokenId());
        }
    }

    private Address tokenAddress() {
        return tokenAddress.get();
    }

    private String tokenType() {
        return tokenType.get();
    }

    private BigInteger tokenId() {
        return tokenId.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setGovernanceToken(Address _address, String _type, @Optional BigInteger _id) {
        onlyOwner();
        var type = _type.toLowerCase();
        switch (type) {
            case IRC2:
            case IRC31:
                tokenType.set(type);
                break;
            default:
                Context.revert("InvalidTokenType");
        }
        tokenAddress.set(_address);
        if (IRC31.equals(type)) {
            tokenId.set(_id);
        }
    }

    @External(readonly=true)
    public BigInteger minimumThreshold() {
        return minimumThreshold.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void setMinimumThreshold(BigInteger _amount) {
        onlyOwner();
        Context.require(_amount.signum() > 0, "Minimum threshold must be positive");
        minimumThreshold.set(_amount);
    }

    private void checkThresholdOrThrow(Address holder) {
        var tokenProxy = new TokenProxy(tokenAddress(), tokenType(), tokenId());
        var balance = tokenProxy.balanceOf(holder);
        Context.require(minimumThreshold().compareTo(balance) <= 0, "MinimumThresholdNotMet");
    }

    private void checkEndTimeOrThrow(BigInteger _endTime) {
        var now = Context.getBlockTimestamp();
        var minimumEnd = DAY_IN_MICROSECONDS.longValue();
        Context.require(_endTime.longValue() > now + minimumEnd, "InvalidEndTime");
    }

    private BigInteger getNextId() {
        BigInteger _id = proposalId.getOrDefault(BigInteger.ZERO);
        _id = _id.add(BigInteger.ONE);
        proposalId.set(_id);
        return _id;
    }

    @External
    public void submitProposal(BigInteger _endTime, String _ipfsHash) {
        Address sender = Context.getCaller();
        Context.require(!sender.isContract(), "Only EOA can submit proposal");
        checkThresholdOrThrow(sender);
        checkEndTimeOrThrow(_endTime);

        BigInteger pid = getNextId();
        Proposal pl = new Proposal(_endTime, _ipfsHash, Proposal.STATUS_ACTIVE);
        proposals.set(pid, pl);
        ProposalSubmitted(pid, sender);
    }

    @External
    public void vote(BigInteger _proposalId, String _vote) {

    }

    @External
    public void cancelProposal(BigInteger _proposalId) {

    }

    @External
    public void closeProposal(BigInteger _proposalId) {

    }

    @External(readonly=true)
    public Map<String, Object> getProposal(BigInteger _proposalId) {
        return Map.of();
    }

    @EventLog(indexed=1)
    public void ProposalSubmitted(BigInteger _proposalId, Address _creator) {}

    @EventLog(indexed=1)
    public void ProposalCanceled(BigInteger _proposalId) {}

    @EventLog(indexed=1)
    public void ProposalClosed(BigInteger _proposalId) {}
}
