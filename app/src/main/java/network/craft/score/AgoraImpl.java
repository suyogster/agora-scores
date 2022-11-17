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

import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.craft.score.TokenProxy.CROWN;
import static network.craft.score.TokenProxy.X_CROWN;

public class AgoraImpl implements AgoraGov {
    public static final BigInteger HOUR_IN_SECONDS = BigInteger.valueOf(3600);
    public static final BigInteger DAY_IN_SECONDS = HOUR_IN_SECONDS.multiply(BigInteger.valueOf(24));
    public static final BigInteger HOUR_IN_MICROSECONDS = HOUR_IN_SECONDS.multiply(BigInteger.valueOf(1_000_000));
    public static final BigInteger MINUTE_IN_MICROSECONDS = HOUR_IN_SECONDS.multiply(BigInteger.valueOf(60));
    public static final BigInteger DAY_IN_MICROSECONDS = DAY_IN_SECONDS.multiply(BigInteger.valueOf(1_000_000));
    private final ArrayDB<Address> tokenAddresses = Context.newArrayDB("token_addresses", Address.class);
    private final ArrayDB<String> tokenTypes = Context.newArrayDB("token_types", String.class);
    private final ArrayDB<String> tokenNames = Context.newArrayDB("token_names", String.class);
    //    private final VarDB<BigInteger> tokenId = Context.newVarDB("token_id", BigInteger.class);
    private final VarDB<BigInteger> minimumThreshold = Context.newVarDB("minimum_threshold", BigInteger.class);

    private final VarDB<BigInteger> proposalId = Context.newVarDB("proposal_id", BigInteger.class);
    private final DictDB<BigInteger, Proposal> proposals = Context.newDictDB("proposals", Proposal.class);
    // proposalId => holder => token votes
    private final BranchDB<BigInteger, DictDB<Address, TokenVote>> tokenVotes = Context.newBranchDB("token_votes", TokenVote.class);
    private final DictDB<BigInteger, Votes> votes = Context.newDictDB("votes_sum", Votes.class);
    private final BranchDB<BigInteger, ArrayDB<Address>> voters = Context.newBranchDB("voters", Address.class);
    private final ArrayDB<Address> whitelistAddress = Context.newArrayDB("whitelist_address", Address.class);

    @External(readonly=true)
    public String name() {
        return "Gangstaverse DAO";
    }

    private void checkCallerOrThrow(Address caller, String errMsg) {
        Context.require(Context.getCaller().equals(caller), errMsg);
    }

    private void onlyOwner() {
        checkCallerOrThrow(Context.getOwner(), "OnlyOwner");
    }

    public static <T> List<T> arrayDbToList(ArrayDB<T> db) {
        @SuppressWarnings("unchecked") T[] addressList = (T[]) new Object[db.size()];
        for (int i = 0; i < db.size(); i++) {
            addressList[i] = db.get(i);
        }
        return List.of(addressList);
    }

    private void onlyWhitelistedAddress(){
        List<Address> whitelistAddress = arrayDbToList(this.whitelistAddress);
        Context.require(whitelistAddress.contains(Context.getCaller()), "Only Whitelisted Addresses");
    }

    @External
    public void whitelistAddress(Address address){
        this.onlyOwner();
        this.whitelistAddress.add(address);
    }

    @External(readonly = true)
    public List<Address> getWhitelistedAddress(){
        return arrayDbToList(this.whitelistAddress);
    }

    @External(readonly=true)
    public Map<String, Object> governanceTokenInfo() {
        var typeList = tokenTypes();
        if (typeList == null) {
            return Map.of();
        }
        Address[] addresses = new Address[typeList.size()];
        for (int i = 0; i < typeList.size(); i++) {
            addresses[i] = tokenAddresses.get(i);
        }
        return Map.of(
                "_address", addresses,
                "_type", typeList);
    }

    private List<Address> tokenAddresses() {
        return arrayDbToList(tokenAddresses);
    }

    private List<String> tokenTypes() {
        return arrayDbToList(tokenTypes);
    }

//    private BigInteger tokenId() {
//        return tokenId.getOrDefault(BigInteger.ZERO);
//    }

    @External
    public void setGovernanceToken(Address _address, String _type, String name, @Optional BigInteger _id) {
        onlyOwner();
//        Context.require(tokenAddresses().size() > 0, "GovernanceTokenAlreadySet");
        Context.require(name.equals(CROWN) || name.equals(X_CROWN), "GovernanceTokenNotSupported");
        var type = _type.toLowerCase();
        switch (type) {
            case TokenProxy.IRC2:
                tokenAddresses.add(_address);
                tokenTypes.add(TokenProxy.IRC2);
                tokenNames.add(name);
                break;
            case TokenProxy.IRC31:
                Context.revert("NotSupported");
                break;
            default:
                Context.revert("InvalidTokenType");
        }
//        if (TokenProxy.IRC31.equals(type)) {
//            tokenId.set(_id);
//        }
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

    @External(readonly=true)
    public BigInteger lastProposalId() {
        return proposalId.getOrDefault(BigInteger.ZERO);
    }

    private void checkEndTimeOrThrow(BigInteger _endTime) {
        var now = Context.getBlockTimestamp();
        var minimumEnd = DAY_IN_MICROSECONDS.longValue();
        Context.require(_endTime.longValue() > now + minimumEnd, "InvalidEndTime");
    }

    private BigInteger getNextId() {
        BigInteger _id = lastProposalId();
        _id = _id.add(BigInteger.ONE);
        proposalId.set(_id);
        return _id;
    }

    @External
    public void submitProposal(BigInteger _endTime, String _ipfsHash) {
        this.onlyWhitelistedAddress();
        Address sender = Context.getCaller();
        Context.require(!sender.isContract(), "Only EOA can submit proposal");
        checkEndTimeOrThrow(_endTime);

        BigInteger crownSnapshotId = BigInteger.ZERO;
        BigInteger xCrownSnapshotId = BigInteger.ZERO;

        for (int i = 0; i < tokenAddresses.size(); i++) {
            var tokenProxy = new TokenProxy(tokenAddresses.get(i), tokenTypes.get(i));
            Context.println("LOG 1 ---" + tokenAddresses.get(i) + tokenTypes.get(i));
            if(tokenNames.get(i).equals(X_CROWN)){
                xCrownSnapshotId = tokenProxy.generateSnapshotAndFetchId();
            }else {
                crownSnapshotId = tokenProxy.generateSnapshotAndFetchId();
            }
        }

        Context.require(
                !crownSnapshotId.equals(BigInteger.ZERO)
                        || !xCrownSnapshotId.equals(BigInteger.ZERO), "InvalidSnapshotOrRatio");


//        --- Is disabled for now. Only whitelisted address can submit the proposal ---
//        var balance = tokenProxy.balanceOf(sender);
//        Context.require(minimumThreshold().compareTo(balance) <= 0, "MinimumThresholdNotMet");

        BigInteger pid = getNextId();
        long createTime = Context.getBlockTimestamp();
        long endTime = _endTime.longValue();
        Proposal pl = new Proposal(sender, createTime, endTime, _ipfsHash, Proposal.STATUS_ACTIVE,
                crownSnapshotId.intValue(), xCrownSnapshotId.intValue());
        proposals.set(pid, pl);
        ProposalSubmitted(pid, sender);
    }

    @External
    public void vote(BigInteger _proposalId, String _vote) {
        Address sender = Context.getCaller();
        Context.require(!sender.isContract(), "Only EOA can submit proposal");

        Proposal pl = proposals.get(_proposalId);
        Context.require(pl != null, "InvalidProposalId");
        Context.require(pl.getStatus() == Proposal.STATUS_ACTIVE, "ProposalNotActive");

        var balance = BigInteger.ZERO;

        for (int i = 0; i < tokenAddresses.size(); i++) {
            var tokenProxy = new TokenProxy(tokenAddresses.get(i), tokenTypes.get(i));
            if(tokenNames.get(i).equals(X_CROWN)){
                balance = balance.add(tokenProxy.balanceOfCrownAt(sender, pl.getXCrownSnapshotId()));
            }else {
                balance = balance.add(tokenProxy.balanceOfAt(sender, pl.getCrownSnapshotId()));
            }
        }

        Context.require(balance.signum() > 0, "NotTokenHolder");

        var vote = _vote.toLowerCase();
        Context.require(Votes.isValid(vote), "InvalidVoteType");

        Context.require(tokenVotes.at(_proposalId).get(sender) == null, "AlreadyVoted");
        tokenVotes.at(_proposalId).set(sender, new TokenVote(vote, balance));
        var vs = votes.get(_proposalId);
        if (vs == null) {
            vs = new Votes();
        }
        vs.increase(vote, balance);
        votes.set(_proposalId, vs);
        voters.at(_proposalId).add(sender);
    }

    @External
    public void cancelProposal(BigInteger _proposalId) {
        Address sender = Context.getCaller();
        Proposal pl = proposals.get(_proposalId);
        Context.require(pl != null, "InvalidProposalId");
        Context.require(pl.getCreator().equals(sender), "NotCreator");
        Context.require(pl.getStatus() == Proposal.STATUS_ACTIVE, "ProposalNotActive");

        long now = Context.getBlockTimestamp();
        long graceTime = 3 * HOUR_IN_MICROSECONDS.longValue();
        Context.require(pl.getStartTime() + graceTime > now, "GraceTimePassed");

        pl.setStatus(Proposal.STATUS_CANCELED);
        proposals.set(_proposalId, pl);
        ProposalCanceled(_proposalId);
    }

    @External
    public void closeProposal(BigInteger _proposalId) {
        Proposal pl = proposals.get(_proposalId);
        Context.require(pl != null, "InvalidProposalId");
        Context.require(pl.getStatus() == Proposal.STATUS_ACTIVE, "ProposalNotActive");

        long now = Context.getBlockTimestamp();
        Context.require(pl.getEndTime() <= now, "EndTimeNotReached");

        pl.setStatus(Proposal.STATUS_CLOSED);
        proposals.set(_proposalId, pl);
        ProposalClosed(_proposalId);
    }

    @External(readonly=true)
    public Map<String, Object> getProposal(BigInteger _proposalId) {

        Proposal pl = proposals.get(_proposalId);
        Context.require(pl != null, "InvalidProposalId");

        var vs = votes.get(_proposalId);
        if (vs == null) {
            vs = new Votes();
        }
        return Map.ofEntries(
                Map.entry("_proposalId", _proposalId),
                Map.entry("_creator", pl.getCreator()),
                Map.entry("_status", Proposal.STATUS_MSG[pl.getStatus()]),
                Map.entry("_crownSnapshotId", pl.getCrownSnapshotId()),
                Map.entry("_xCrownSnapshotId", pl.getXCrownSnapshotId()),
                Map.entry("_endTime", pl.getEndTime()),
                Map.entry("_startTime", pl.getStartTime()),
                Map.entry("_ipfsHash", pl.getIpfsHash()),
                Map.entry("_forVoices", vs.getFor()),
                Map.entry("_againstVoices", vs.getAgainst()),
                Map.entry("_abstainVoices", vs.getAbstain())
        );
    }

    @External(readonly=true)
    public Map<String, Object> getVote(Address _voter, BigInteger _proposalId) {
        Proposal pl = proposals.get(_proposalId);
        var tokenVote = tokenVotes.at(_proposalId).get(_voter);

        if (tokenVote != null) {
            return Map.of(
                    "_vote", tokenVote.getVote(),
                    "_power", tokenVote.getAmount()
            );
        }
        return Map.of();
    }

    @External(readonly=true)
    public Map<String, Object> getVoteDetail(BigInteger _proposalId, int offset, int limit) {
        List<Address> voters = arrayDbToList(this.voters.at(_proposalId));
        List<Map<String, Object>> votesDetail = new ArrayList<>();
        int totalVotes = voters.size();
        int maxCount = Math.min(offset + limit, voters.size());

        for (int i = maxCount - 1; i >= offset ; i--) {
            Address voter = voters.get(i);
            votesDetail.add(Map.of(voter.toString(), this.getVote(voter,_proposalId)));
        }

        return Map.of("_totalVotes", totalVotes,
                "_votesList", votesDetail);
    }

    @External(readonly=true)
    public int getVotersCount(BigInteger _proposalId) {
        List<Address> voters = arrayDbToList(this.voters.at(_proposalId));
        return voters.size();
    }

    @EventLog(indexed=1)
    public void ProposalSubmitted(BigInteger _proposalId, Address _creator) {}

    @EventLog(indexed=1)
    public void ProposalCanceled(BigInteger _proposalId) {}

    @EventLog(indexed=1)
    public void ProposalClosed(BigInteger _proposalId) {}
}
