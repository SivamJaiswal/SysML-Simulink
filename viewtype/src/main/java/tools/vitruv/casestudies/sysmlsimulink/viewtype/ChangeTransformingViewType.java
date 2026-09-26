package tools.vitruv.casestudies.sysmlsimulink.viewtype;

import tools.vitruv.casestudies.sysmlsimulink.viewtype.impl.ChangeTransformingViewTypeImpl;

import java.util.List;
import java.util.function.Function;

import tools.vitruv.change.atomic.EChange;
import tools.vitruv.change.atomic.hid.HierarchicalId;
import tools.vitruv.framework.views.impl.IdentityMappingViewType;

public abstract class ChangeTransformingViewType extends IdentityMappingViewType {

  public ChangeTransformingViewType(String name) {
    super(name);
  }

  public abstract boolean registerFilter(Function<List<EChange<HierarchicalId>>, List<EChange<HierarchicalId>>> filter);

  public abstract boolean unregisterFilter(Function<List<EChange<HierarchicalId>>, List<EChange<HierarchicalId>>> filter);

  public static ChangeTransformingViewType create(String name) {
    return new ChangeTransformingViewTypeImpl(name);
  }
}
