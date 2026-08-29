import { Chip, List, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { IMenuitems } from './MenuItems';

interface NavMenuItemType {
  item: IMenuitems;
  pathTo: string;
}
const NavMenuItem = ({ item, pathTo }: NavMenuItemType) => {
  const { icon: Icon } = item;
  const itemIcon = Icon ? <Icon /> : null;
  return (
    <List component="li" disablePadding key={item?.id && item.title}>
      <ListItemButton
        component={RouterLink}
        to={item?.href || '/'}
        disabled={item?.disabled}
        selected={pathTo === item?.href}
      >
        <ListItemIcon
          sx={{
            py: 0.4,
            px: 0,
            ...(!item.available && {
              color: 'action.active',
              opacity: 0.9,
            }),
          }}
        >
          {itemIcon}
        </ListItemIcon>
        <ListItemText
          sx={{
            ...(!item.available && {
              color: 'action.active',
              opacity: 0.9,
            }),
          }}
        >
          {<>{`${item?.title}`}</>}
          <br />
          {item?.subtitle ? <Typography variant="caption">{item.subtitle}</Typography> : ''}
        </ListItemText>

        {!item?.chip ? null : (
          <Chip
            color={item?.chipColor}
            variant={item?.variant ? item?.variant : 'outlined'}
            size="small"
            label={item?.chip}
            sx={({ palette, shape, typography }) => ({
              borderRadius: shape.borderRadius * 3,
              ...typography.caption,
              ...(pathTo === item?.href
                ? {
                    bgcolor: palette.text.disabled,
                    color: palette.primary.main,
                  }
                : {
                    bgcolor: palette.text.primary,
                    color: palette.common.white,
                  }),
            })}
          />
        )}
      </ListItemButton>
    </List>
  );
};

export default NavMenuItem;
