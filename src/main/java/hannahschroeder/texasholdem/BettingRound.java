package hannahschroeder.texasholdem;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

class BettingRound {
    private Scanner in;
    private Table table;
    private List<Player> allPlayers;
    private List<Player> activePlayers;
    private Player dealer;
    private int currentBet = 0;
    private Map<Player, Integer> playerBets = new HashMap<>();
    
    public BettingRound(Table table, Scanner in) {
        this.in = in;
        this.table = table;
        allPlayers = table.getAllPlayers();
        activePlayers = table.getActivePlayers();
        dealer = table.getDealer();

        for (Player player : activePlayers) {
            playerBets.put(player, 0);
            player.setCompletedTurn(false);
        }
    }

    /**
     * plays a round of betting
     * @param isPreFlop true if the betting round is the pre-flop
     * @param roundWinners list of round winners
     * @return true if betting round resulted in a default win
     */
    public boolean play(boolean isPreFlop, List<Player> roundWinners) {
        Player currentPlayer = getNextPlayer(dealer);
        int smallBlind = table.getSmallBlind();
        int bigBlind = 2*smallBlind;

        if (isPreFlop) {
            System.out.printf("%s is dealer.%n", dealer.getName());

            // posting of small blind
            increaseBet(currentPlayer, smallBlind, 0);
            System.out.printf("%s posts small blind of %d.%n", currentPlayer.getName(), currentBet);

            currentPlayer = getNextPlayer(currentPlayer);

            // posting of big blind
            currentPlayer.setBigBlind(true);
            increaseBet(currentPlayer, bigBlind, 0);
            System.out.printf("%s posts big blind of %d.%n", currentPlayer.getName(), currentBet);

            currentPlayer = getNextPlayer(currentPlayer);
        }

        /**
         * TODO: handle side pots/inability to match bet
         */
        do {
            PlayerAction action;
            EnumSet<PlayerAction> validActions;
            // TODO: how much current player can match the bet affects options
            if (currentPlayer.isBigBlind() && currentBet == bigBlind) {
                validActions = EnumSet.of(PlayerAction.CHECK, PlayerAction.RAISE, PlayerAction.ALL_IN, PlayerAction.FOLD);
            } else if (currentBet > 0) {
                validActions = EnumSet.of(PlayerAction.CALL, PlayerAction.RAISE, PlayerAction.ALL_IN, PlayerAction.FOLD);
            } else {
                validActions = EnumSet.of(PlayerAction.CHECK, PlayerAction.BET, PlayerAction.ALL_IN, PlayerAction.FOLD);
            }
            action = PlayerAction.getActionFromScanner(in, currentPlayer, validActions);

            int existingBet = playerBets.get(currentPlayer);
            int allInAmount = currentPlayer.getStackValue() + existingBet;

            switch (action) {
                case BET: { // refactor to utilize increaseBet() method
                    int betAmount = getAmount(currentPlayer, bigBlind, currentPlayer.getStackValue());
                    currentBet = betAmount;
                    currentPlayer.removeFromStack(betAmount);
                    playerBets.put(currentPlayer, betAmount);
                    System.out.printf("%s bets %d%n", currentPlayer.getName(), betAmount);
                    setTurnsIncomplete();
                    break;
                }
                case RAISE: { // refactor to utilize increaseBet() method
                    int minRaise = currentBet + bigBlind;
                    int raiseAmount = getAmount(currentPlayer, minRaise, allInAmount);
                    currentBet = raiseAmount;
                    currentPlayer.removeFromStack(raiseAmount - existingBet);
                    playerBets.put(currentPlayer, raiseAmount);
                    System.out.printf("%s raises to %d%n", currentPlayer.getName(), raiseAmount);
                    setTurnsIncomplete();
                    break;
                }
                case CALL: {
                    currentPlayer.removeFromStack(currentBet - playerBets.get(currentPlayer));
                    playerBets.put(currentPlayer, currentBet);
                    System.out.printf("%s calls%n", currentPlayer.getName());
                    break;
                }
                case CHECK: {
                    System.out.printf("%s checks%n", currentPlayer.getName());
                    break;
                }
                case ALL_IN: {
                    // TODO: Handle all in
                    if (allInAmount > currentBet) { // refactor to utilize increaseBet() method
                        currentBet = allInAmount;
                        setTurnsIncomplete();
                    } else if (allInAmount < currentBet) {
                        // TODO: create side pots
                    }
                    currentPlayer.removeFromStack(allInAmount - existingBet);
                    playerBets.put(currentPlayer, allInAmount);
                    System.out.printf("%s goes all in%n", currentPlayer.getName());
                    break;
                }
                case FOLD: {
                    System.out.printf("%s folds%n", currentPlayer.getName());
                    currentPlayer.fold();
                }
            }

            currentPlayer.setCompletedTurn(true);

            if (currentPlayer.isBigBlind()) {
                currentPlayer.setBigBlind(false);
            }

            Player nextPlayer = getNextPlayer(currentPlayer);
            if (action == PlayerAction.FOLD) {
                activePlayers.remove(currentPlayer);
                currentPlayer = nextPlayer;
                if (checkDefaultWin(roundWinners)) {
                    return true;
                }
            } else {
                currentPlayer = nextPlayer;
            }
        } while (!currentPlayer.completedTurn());

        // TODO: handle side pot scenario
        int sumBets = 0;
        for (Map.Entry<Player, Integer> entry : playerBets.entrySet()) {
            int bet = entry.getValue();
            sumBets += bet;
            entry.setValue(0);
        }
        int oldPot = table.getCenterPot();
        table.setCenterPot(oldPot + sumBets);

        return false;
    }

    //TODO: get amount from scanner & check that it's within the range
    private int getAmount(Player player, int min, int max) {
        return min;
    }

    /**
     * resets the going bet for the table, tracks who made the last bet/raise,
     * and transfers the player's chips to their local betting pot
     * @param player player who is raising/starting the betting
     * @param betAmount total amount of the bet
     * @param previousBet amount player had previously contributed towards the bet
     */
    private void increaseBet(Player player, int betAmount, int previousBet) {
        currentBet = betAmount;

        int transferAmount = betAmount - previousBet;
        player.removeFromStack(transferAmount);
        playerBets.put(player, betAmount);
    }

    private Player getNextPlayer(Player currentPlayer) {
        Player previousPlayer = currentPlayer;
        Player nextPlayer;
        do {
            int previousPlayerId = previousPlayer.getId();
            int nextPlayerId;

            if (previousPlayerId == allPlayers.size() - 1) {
                nextPlayerId = 0;
            } else {
                nextPlayerId = previousPlayerId + 1;
            }

            nextPlayer = table.getPlayerById(nextPlayerId);
            previousPlayer = nextPlayer;
        } while (nextPlayer.isFolded() || nextPlayer.isBusted());

        return nextPlayer;
    }

    /**
     * 
     * @param roundWinners a list to fill with the winning players
     * @return true if there was a winner, false otherwise
     */
    private boolean checkDefaultWin(List<Player> roundWinners) {
        Player winner = null;

        if (activePlayers.size() == 1) {
            winner = activePlayers.get(0);
            winner.dontReveal();
            roundWinners.add(winner);
            // TODO: put player bets into table pot
            return true;
        }

        return false;
    }

    private void setTurnsIncomplete() {
        for (Player player : allPlayers) {
            if (!player.isFolded() && !player.isBusted() && player.getStackValue() > 0) {
                player.setCompletedTurn(false);
            }
        }
    }
}