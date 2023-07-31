package org.whispersystems.signalservice.api.crypto;


import java.util.Optional;
import java.util.logging.Logger;

public class UnidentifiedAccessPair {

  private final Optional<UnidentifiedAccess> targetUnidentifiedAccess;
  private final Optional<UnidentifiedAccess> selfUnidentifiedAccess;
    private static final Logger LOG = Logger.getLogger(UnidentifiedAccessPair.class.getName());

    public UnidentifiedAccessPair(UnidentifiedAccess targetUnidentifiedAccess, UnidentifiedAccess selfUnidentifiedAccess) {
        LOG.finest("Create UAP, me[0] = " +selfUnidentifiedAccess.getUnidentifiedAccessKey()[0] +
                    " and them[0] = " + targetUnidentifiedAccess.getUnidentifiedAccessKey()[0]);
        this.targetUnidentifiedAccess = Optional.of(targetUnidentifiedAccess);
        this.selfUnidentifiedAccess = Optional.of(selfUnidentifiedAccess);
    }

  public Optional<UnidentifiedAccess> getTargetUnidentifiedAccess() {
    return targetUnidentifiedAccess;
  }

  public Optional<UnidentifiedAccess> getSelfUnidentifiedAccess() {
    return selfUnidentifiedAccess;
  }
}
